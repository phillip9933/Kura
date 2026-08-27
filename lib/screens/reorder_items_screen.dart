import 'package:flutter/material.dart';
import 'package:kura/models/provider_helper.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:provider/provider.dart';

class ReorderItemsScreen extends StatelessWidget {
  const ReorderItemsScreen({super.key, required this.section});

  final WalletSection section;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Reorder $_sectionTitle'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Done'),
          ),
        ],
      ),
      body: switch (section) {
        WalletSection.payments => const _WalletReorderList(),
        WalletSection.passes => const _PassReorderList(),
        WalletSection.identity => const _IdentityReorderList(),
      },
    );
  }

  String get _sectionTitle => switch (section) {
    WalletSection.payments => 'Payments',
    WalletSection.passes => 'Passes',
    WalletSection.identity => 'Identity',
  };
}

class _WalletReorderList extends StatelessWidget {
  const _WalletReorderList();

  @override
  Widget build(BuildContext context) {
    final wallets = context.watch<WalletProvider>().wallets;
    return _ReorderList(
      itemCount: wallets.length,
      itemBuilder: (context, index) {
        final wallet = wallets[index];
        return _ReorderTile(
          key: ValueKey(wallet.id),
          title: wallet.name,
          subtitle: wallet.network ?? 'Payment card',
          canMoveUp: index > 0,
          canMoveDown: index < wallets.length - 1,
          onMoveUp: () =>
              context.read<WalletProvider>().reorderWallets(index, index - 1),
          onMoveDown: () =>
              context.read<WalletProvider>().reorderWallets(index, index + 2),
        );
      },
      onReorder: context.read<WalletProvider>().reorderWallets,
    );
  }
}

class _PassReorderList extends StatelessWidget {
  const _PassReorderList();

  @override
  Widget build(BuildContext context) {
    final passes = context.watch<PassProvider>().passes;
    return _ReorderList(
      itemCount: passes.length,
      itemBuilder: (context, index) {
        final pass = passes[index];
        return _ReorderTile(
          key: ValueKey(pass.id),
          title: pass.organizationName,
          subtitle: pass.description?.isNotEmpty == true
              ? pass.description!
              : pass.type,
          canMoveUp: index > 0,
          canMoveDown: index < passes.length - 1,
          onMoveUp: () =>
              context.read<PassProvider>().reorderPasses(index, index - 1),
          onMoveDown: () =>
              context.read<PassProvider>().reorderPasses(index, index + 2),
        );
      },
      onReorder: context.read<PassProvider>().reorderPasses,
    );
  }
}

class _IdentityReorderList extends StatelessWidget {
  const _IdentityReorderList();

  @override
  Widget build(BuildContext context) {
    final identities = context.watch<IdentityProvider>().identities;
    return _ReorderList(
      itemCount: identities.length,
      itemBuilder: (context, index) {
        final identity = identities[index];
        return _ReorderTile(
          key: ValueKey(identity.id),
          title: identity.name,
          subtitle: identity.cardType,
          canMoveUp: index > 0,
          canMoveDown: index < identities.length - 1,
          onMoveUp: () => context.read<IdentityProvider>().reorderIdentities(
            index,
            index - 1,
          ),
          onMoveDown: () => context.read<IdentityProvider>().reorderIdentities(
            index,
            index + 2,
          ),
        );
      },
      onReorder: context.read<IdentityProvider>().reorderIdentities,
    );
  }
}

class _ReorderList extends StatelessWidget {
  const _ReorderList({
    required this.itemCount,
    required this.itemBuilder,
    required this.onReorder,
  });

  final int itemCount;
  final IndexedWidgetBuilder itemBuilder;
  final Future<void> Function(int oldIndex, int newIndex) onReorder;

  @override
  Widget build(BuildContext context) {
    if (itemCount < 2) {
      return const Center(
        child: Text('Add at least two items to reorder them.'),
      );
    }
    return ReorderableListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
      itemCount: itemCount,
      itemBuilder: itemBuilder,
      onReorderItem: onReorder,
    );
  }
}

class _ReorderTile extends StatelessWidget {
  const _ReorderTile({
    super.key,
    required this.title,
    required this.subtitle,
    required this.canMoveUp,
    required this.canMoveDown,
    required this.onMoveUp,
    required this.onMoveDown,
  });

  final String title;
  final String subtitle;
  final bool canMoveUp;
  final bool canMoveDown;
  final VoidCallback onMoveUp;
  final VoidCallback onMoveDown;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: const Icon(Icons.drag_handle_rounded),
        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: 'Move up',
              onPressed: canMoveUp ? onMoveUp : null,
              icon: const Icon(Icons.keyboard_arrow_up_rounded),
            ),
            IconButton(
              tooltip: 'Move down',
              onPressed: canMoveDown ? onMoveDown : null,
              icon: const Icon(Icons.keyboard_arrow_down_rounded),
            ),
          ],
        ),
      ),
    );
  }
}
