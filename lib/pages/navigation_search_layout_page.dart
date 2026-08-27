import 'package:flutter/material.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:provider/provider.dart';

class NavigationSearchLayoutPage extends StatelessWidget {
  const NavigationSearchLayoutPage({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<StartupSettingsProvider>();
    final isDark = context.watch<ThemeProvider>().isDarkMode;
    final divider = Divider(
      color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFE8E8E8),
      height: 1,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Navigation & Search Layout')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.visibility_outlined),
                  title: const Text('Visible Tabs'),
                  subtitle: const Text(
                    'Choose the sections shown in main navigation',
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _VisibleTabChip(
                        label: 'Payments',
                        selected: provider.showPaymentsTab,
                        onSelected: (selected) =>
                            provider.setTabVisibility(0, selected),
                      ),
                      _VisibleTabChip(
                        label: 'Passes',
                        selected: provider.showPassesTab,
                        onSelected: (selected) =>
                            provider.setTabVisibility(1, selected),
                      ),
                      _VisibleTabChip(
                        label: 'Identity',
                        selected: provider.showIdentityTab,
                        onSelected: (selected) =>
                            provider.setTabVisibility(2, selected),
                      ),
                    ],
                  ),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.navigation_outlined),
                  title: const Text('Show Bottom Navigation'),
                  subtitle: const Text(
                    'Show or hide the Payments, Passes, and Identity bar',
                  ),
                  trailing: Switch(
                    value: provider.showBottomNavigationBar,
                    onChanged: provider.setShowBottomNavigationBar,
                  ),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.vertical_align_bottom_rounded),
                  title: const Text('Control Row Position'),
                  subtitle: const Text(
                    'Move section controls above or below content',
                  ),
                  trailing: TextButton(
                    onPressed: () => provider.setControlRowPosition(
                      provider.controlRowPosition == ControlRowPosition.top
                          ? ControlRowPosition.bottom
                          : ControlRowPosition.top,
                    ),
                    child: Text(
                      _getControlRowPositionDisplayName(
                        provider.controlRowPosition,
                      ),
                    ),
                  ),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.search_rounded),
                  title: const Text('Show Search'),
                  subtitle: const Text(
                    'Show or hide the search bar on Payments, Passes, and Identity',
                  ),
                  trailing: Switch(
                    value: provider.isPassSearchEnabled,
                    onChanged: provider.setPassSearchEnabled,
                  ),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.tune_rounded),
                  title: const Text('Search Style'),
                  subtitle: Text(
                    _getSearchStyleDisplayName(provider.passSearchStyle),
                  ),
                  trailing: TextButton(
                    onPressed: provider.isPassSearchEnabled
                        ? () => provider.setPassSearchStyle(
                            provider.passSearchStyle == PassSearchStyle.alwaysOn
                                ? PassSearchStyle.icon
                                : PassSearchStyle.alwaysOn,
                          )
                        : null,
                    child: Text(
                      _getSearchStyleDisplayName(provider.passSearchStyle),
                    ),
                  ),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.swap_vert_rounded),
                  title: const Text('Search Position'),
                  subtitle: const Text(
                    'Move the search bar above or below content',
                  ),
                  trailing: TextButton(
                    onPressed:
                        provider.isPassSearchEnabled &&
                            provider.passSearchStyle == PassSearchStyle.alwaysOn
                        ? () => provider.setSearchBarPosition(
                            provider.searchBarPosition == SearchBarPosition.top
                                ? SearchBarPosition.bottom
                                : SearchBarPosition.top,
                          )
                        : null,
                    child: Text(
                      _getSearchPositionDisplayName(provider.searchBarPosition),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _getSearchStyleDisplayName(PassSearchStyle style) {
    return switch (style) {
      PassSearchStyle.alwaysOn => 'Search Bar',
      PassSearchStyle.icon => 'Search Button',
    };
  }

  String _getSearchPositionDisplayName(SearchBarPosition position) {
    return switch (position) {
      SearchBarPosition.top => 'Top',
      SearchBarPosition.bottom => 'Bottom',
    };
  }

  String _getControlRowPositionDisplayName(ControlRowPosition position) {
    return position == ControlRowPosition.top ? 'Top' : 'Bottom';
  }
}

class _VisibleTabChip extends StatelessWidget {
  const _VisibleTabChip({
    required this.label,
    required this.selected,
    required this.onSelected,
  });

  final String label;
  final bool selected;
  final ValueChanged<bool> onSelected;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return FilterChip(
      label: Text(
        label,
        style: TextStyle(
          color: selected ? colorScheme.onPrimary : colorScheme.onSurface,
          fontWeight: FontWeight.w600,
        ),
      ),
      selected: selected,
      onSelected: onSelected,
      selectedColor: colorScheme.primary,
      backgroundColor: colorScheme.surfaceContainerHighest,
      checkmarkColor: colorScheme.onPrimary,
    );
  }
}
