import 'package:flutter/material.dart';
import 'package:kura/models/db_helper.dart';
import 'package:kura/models/provider_helper.dart';
import 'package:provider/provider.dart';

class ArchiveScreen extends StatefulWidget {
  const ArchiveScreen({super.key});

  @override
  State<ArchiveScreen> createState() => _ArchiveScreenState();
}

class _ArchiveScreenState extends State<ArchiveScreen> {
  Future<_ArchivedItems>? _archivedItems;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  void _reload() {
    setState(() {
      _archivedItems =
          Future.wait([
            DatabaseHelper.instance.getArchivedWallets(),
            PassDatabaseHelper.instance.getArchivedPasses(),
            IdentityDatabaseHelper.instance.getArchivedIdentities(),
          ]).then(
            (items) => _ArchivedItems(
              wallets: items[0] as List<Wallet>,
              passes: items[1] as List<Pass>,
              identities: items[2] as List<IdentityCard>,
            ),
          );
    });
  }

  Future<void> _confirmPermanentDelete({
    required String name,
    required Future<void> Function() onDelete,
  }) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete permanently?'),
        content: Text(
          'Permanently delete "$name" and its stored images? This cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    await onDelete();
    if (mounted) _reload();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Archive')),
      body: FutureBuilder<_ArchivedItems>(
        future: _archivedItems,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          final items = snapshot.data;
          if (items == null || items.isEmpty) {
            return const Center(child: Text('No archived items.'));
          }
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            children: [
              if (items.wallets.isNotEmpty) ...[
                const _ArchiveSectionHeader('Payments'),
                for (final wallet in items.wallets)
                  _ArchiveTile(
                    title: wallet.name,
                    subtitle: wallet.network ?? 'Payment card',
                    onRestore: () async {
                      await context.read<WalletProvider>().restoreWallet(
                        wallet.id!,
                      );
                      _reload();
                    },
                    onDelete: () => _confirmPermanentDelete(
                      name: wallet.name,
                      onDelete: () => context
                          .read<WalletProvider>()
                          .deleteWallet(wallet.id!),
                    ),
                  ),
              ],
              if (items.passes.isNotEmpty) ...[
                const _ArchiveSectionHeader('Passes'),
                for (final pass in items.passes)
                  _ArchiveTile(
                    title: pass.organizationName,
                    subtitle: pass.description?.isNotEmpty == true
                        ? pass.description!
                        : pass.type,
                    onRestore: () async {
                      await context.read<PassProvider>().restorePass(pass.id!);
                      _reload();
                    },
                    onDelete: () => _confirmPermanentDelete(
                      name: pass.organizationName,
                      onDelete: () =>
                          context.read<PassProvider>().deletePass(pass.id!),
                    ),
                  ),
              ],
              if (items.identities.isNotEmpty) ...[
                const _ArchiveSectionHeader('Identity'),
                for (final identity in items.identities)
                  _ArchiveTile(
                    title: identity.name,
                    subtitle: identity.cardType,
                    onRestore: () async {
                      await context.read<IdentityProvider>().restoreIdentity(
                        identity.id!,
                      );
                      _reload();
                    },
                    onDelete: () => _confirmPermanentDelete(
                      name: identity.name,
                      onDelete: () => context
                          .read<IdentityProvider>()
                          .deleteIdentity(identity.id!),
                    ),
                  ),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _ArchivedItems {
  const _ArchivedItems({
    required this.wallets,
    required this.passes,
    required this.identities,
  });

  final List<Wallet> wallets;
  final List<Pass> passes;
  final List<IdentityCard> identities;

  bool get isEmpty => wallets.isEmpty && passes.isEmpty && identities.isEmpty;
}

class _ArchiveSectionHeader extends StatelessWidget {
  const _ArchiveSectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 8, bottom: 8),
      child: Text(title, style: Theme.of(context).textTheme.titleMedium),
    );
  }
}

class _ArchiveTile extends StatelessWidget {
  const _ArchiveTile({
    required this.title,
    required this.subtitle,
    required this.onRestore,
    required this.onDelete,
  });

  final String title;
  final String subtitle;
  final VoidCallback onRestore;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: const Icon(Icons.inventory_2_outlined),
        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: 'Restore',
              onPressed: onRestore,
              icon: const Icon(Icons.unarchive_outlined),
            ),
            IconButton(
              tooltip: 'Delete permanently',
              onPressed: onDelete,
              icon: Icon(
                Icons.delete_outline,
                color: Theme.of(context).colorScheme.error,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
