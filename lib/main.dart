import 'package:flutter/material.dart';
import 'package:kura/models/auto_backup_provider.dart';
import 'package:kura/models/provider_helper.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:kura/models/vault_access_provider.dart';
import 'package:kura/screens/homescreen.dart';
import 'package:kura/services/app_initialization_service.dart';
import 'package:kura/services/auto_backup_service.dart';
import 'package:provider/provider.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final themeProvider = ThemeProvider();
  final startupProvider = StartupSettingsProvider();
  final autoBackupProvider = AutoBackupProvider();
  await Future.wait([
    themeProvider.init(),
    startupProvider.loadStartupSettings(),
    autoBackupProvider.init(),
  ]);
  AutoBackupService.initialize(autoBackupProvider);

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => WalletProvider()),
        ChangeNotifierProvider(create: (_) => PassProvider()),
        ChangeNotifierProvider(create: (_) => IdentityProvider()),
        ChangeNotifierProvider(create: (_) => VaultAccessProvider()),
        ChangeNotifierProvider.value(value: themeProvider),
        ChangeNotifierProvider.value(value: startupProvider),
        ChangeNotifierProvider.value(value: autoBackupProvider),
      ],
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  late final AppLifecycleListener _lifecycleListener;

  @override
  void initState() {
    super.initState();
    _lifecycleListener = AppLifecycleListener(
      onStateChange: (state) {
        if (state == AppLifecycleState.paused ||
            state == AppLifecycleState.detached) {
          _lockVault();
        }
      },
    );
  }

  void _lockVault() {
    if (!mounted) return;
    AutoBackupService.lock();
    context.read<WalletProvider>().clear();
    context.read<PassProvider>().clear();
    context.read<IdentityProvider>().clear();
    context.read<VaultAccessProvider>().lock();
    AppInitializationService.lockVault();
  }

  @override
  void dispose() {
    _lifecycleListener.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Selector<ThemeProvider, ThemeMode>(
      selector: (_, provider) => provider.currentTheme,
      builder: (_, themeMode, _) {
        final themeProvider = context.read<ThemeProvider>();
        return MaterialApp(
          title: 'Kura',
          debugShowCheckedModeBanner: false,
          theme: themeProvider.lightTheme,
          darkTheme: themeProvider.darkTheme,
          themeMode: themeMode,
          home: const VaultAccessGate(),
        );
      },
    );
  }
}

class VaultAccessGate extends StatefulWidget {
  const VaultAccessGate({super.key});

  @override
  State<VaultAccessGate> createState() => _VaultAccessGateState();
}

class _VaultAccessGateState extends State<VaultAccessGate> {
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _unlock());
  }

  Future<void> _unlock() async {
    final access = context.read<VaultAccessProvider>();
    final unlocked = await access.unlock();
    if (!unlocked) {
      if (mounted) {
        setState(
          () => _error = 'Authentication is required to access your vault.',
        );
      }
      return;
    }

    if (!access.beginInitialization()) return;
    try {
      await AppInitializationService.initializeApp();
      if (!mounted || !access.isUnlocked) return;
      setState(() => _error = null);
    } catch (_) {
      access.lock();
      if (mounted) {
        setState(
          () => _error = 'Unable to open the encrypted vault. Try again.',
        );
      }
    } finally {
      access.finishInitialization();
    }
  }

  @override
  Widget build(BuildContext context) {
    final access = context.watch<VaultAccessProvider>();
    if (access.isUnlocked && _error == null) return const HomeScreen();

    final isAuthenticating = access.state == VaultAccessState.authenticating;
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.lock_outline_rounded, size: 64),
              const SizedBox(height: 20),
              const Text(
                'Vault locked',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Text(
                _error ??
                    'Authenticate with your device credential to continue.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: isAuthenticating ? null : _unlock,
                icon: isAuthenticating
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.lock_open_rounded),
                label: Text(
                  isAuthenticating ? 'Authenticating…' : 'Unlock vault',
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
