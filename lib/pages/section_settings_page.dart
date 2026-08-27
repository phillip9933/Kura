import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:kura/models/startup_settings_provider.dart';

class SectionSettingsPage extends StatelessWidget {
  const SectionSettingsPage({super.key, required this.section});

  final WalletSection section;

  String get _title => switch (section) {
    WalletSection.payments => 'Payments Settings',
    WalletSection.passes => 'Passes Settings',
    WalletSection.identity => 'Identity Settings',
  };

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<StartupSettingsProvider>();
    return Scaffold(
      appBar: AppBar(title: Text(_title)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.style_outlined),
                  title: const Text('Card Display'),
                  subtitle: Text(
                    _displayModeLabel(settings.gridModeFor(section)),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => _showDisplayModeDialog(context, settings),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.category_outlined),
                  title: const Text('Manage Categories'),
                  subtitle: Text(
                    '${settings.categoriesFor(section).length} configured',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => ManageCategoriesPage(
                        section: section,
                        settings: settings,
                      ),
                    ),
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.dynamic_form_outlined),
                  title: const Text('Manage Custom Fields'),
                  subtitle: Text(
                    '${settings.customFieldsFor(section).length} configured',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => ManageFormFieldsPage(
                        section: section,
                        settings: settings,
                      ),
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

  String _displayModeLabel(PassGridDisplayMode mode) => switch (mode) {
    PassGridDisplayMode.front => 'Front Image',
    PassGridDisplayMode.back => 'Back Image',
    PassGridDisplayMode.virtualCards => 'Virtual Card',
  };

  Future<void> _showDisplayModeDialog(
    BuildContext context,
    StartupSettingsProvider settings,
  ) async {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Card Display'),
        content: RadioGroup<PassGridDisplayMode>(
          groupValue: settings.gridModeFor(section),
          onChanged: (value) async {
            if (value == null) return;
            await settings.setGridDisplayMode(section, value);
            if (dialogContext.mounted) Navigator.pop(dialogContext);
          },
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: PassGridDisplayMode.values
                .where(
                  (mode) =>
                      section != WalletSection.passes ||
                      mode != PassGridDisplayMode.back,
                )
                .map(
                  (mode) => RadioListTile<PassGridDisplayMode>(
                    title: Text(_displayModeLabel(mode)),
                    value: mode,
                  ),
                )
                .toList(),
          ),
        ),
      ),
    );
  }
}

class ManageCategoriesPage extends StatefulWidget {
  const ManageCategoriesPage({
    super.key,
    required this.section,
    required this.settings,
  });

  final WalletSection section;
  final StartupSettingsProvider settings;

  @override
  State<ManageCategoriesPage> createState() => _ManageCategoriesPageState();
}

class _ManageCategoriesPageState extends State<ManageCategoriesPage> {
  late List<String> _categories;
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    _categories = List.of(widget.settings.categoriesFor(widget.section));
  }

  Future<bool> _save(List<String> previousCategories) async {
    if (_isSaving) return false;
    setState(() => _isSaving = true);
    try {
      await widget.settings.saveCategories(widget.section, _categories);
      return true;
    } catch (_) {
      if (mounted) {
        setState(() => _categories = previousCategories);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Could not save categories. Try again.'),
          ),
        );
      }
      return false;
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  Future<void> _editCategory({int? index}) async {
    final newName = await showDialog<String>(
      context: context,
      builder: (_) => _CategoryNameDialog(
        title: index == null ? 'Add Category' : 'Edit Category',
        initialName: index == null ? '' : _categories[index],
      ),
    );
    if (!mounted || newName == null || newName.isEmpty) return;

    final isDuplicate = _categories.any(
      (item) =>
          item.toLowerCase() == newName.toLowerCase() &&
          item != (index == null ? null : _categories[index]),
    );
    if (isDuplicate) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('A category with this name already exists.'),
        ),
      );
      return;
    }

    final previousCategories = List<String>.of(_categories);
    setState(() {
      if (index == null) {
        _categories.add(newName);
      } else {
        _categories[index] = newName;
      }
    });
    await _save(previousCategories);
  }

  Future<void> _deleteCategory(int index) async {
    final category = _categories[index];
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete Category?'),
        content: Text(
          'Remove "$category" from this sectionâ€™s available category filters? Existing records will not be changed.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final previousCategories = List<String>.of(_categories);
    setState(() => _categories.removeAt(index));
    await _save(previousCategories);
  }

  Future<void> _restoreDefaults() async {
    final defaults = widget.settings.defaultCategoriesFor(widget.section);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Restore Default Categories?'),
        content: const Text(
          'Your current category list will be replaced with the default list. Existing records will not be changed.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Restore'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    final previousCategories = List<String>.of(_categories);
    setState(() => _categories = List<String>.of(defaults));
    if (await _save(previousCategories) && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Default categories restored.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Manage Categories'),
        actions: [
          TextButton.icon(
            onPressed: _isSaving ? null : _restoreDefaults,
            icon: const Icon(Icons.restore_rounded),
            label: const Text('Restore Defaults'),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _isSaving ? null : _editCategory,
        icon: const Icon(Icons.add),
        label: const Text('Add Category'),
      ),
      body: ReorderableListView.builder(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
        itemCount: _categories.length,
        onReorderItem: (oldIndex, newIndex) async {
          if (_isSaving) return;
          final previousCategories = List<String>.of(_categories);
          setState(() {
            final category = _categories.removeAt(oldIndex);
            _categories.insert(newIndex, category);
          });
          await _save(previousCategories);
        },
        itemBuilder: (context, index) {
          final category = _categories[index];
          return Card(
            key: ValueKey(category),
            child: ListTile(
              leading: const Icon(Icons.drag_handle_rounded),
              title: Text(category),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    tooltip: 'Edit category',
                    icon: const Icon(Icons.edit_outlined),
                    onPressed: _isSaving
                        ? null
                        : () => _editCategory(index: index),
                  ),
                  IconButton(
                    tooltip: 'Delete category',
                    icon: const Icon(Icons.delete_outline),
                    onPressed: _isSaving ? null : () => _deleteCategory(index),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _CategoryNameDialog extends StatefulWidget {
  const _CategoryNameDialog({required this.title, required this.initialName});

  final String title;
  final String initialName;

  @override
  State<_CategoryNameDialog> createState() => _CategoryNameDialogState();
}

class _CategoryNameDialogState extends State<_CategoryNameDialog> {
  late final TextEditingController _controller;
  late final FocusNode _focusNode;
  String? _errorText;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialName);
    _focusNode = FocusNode();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final categoryName = _controller.text.trim();
    if (categoryName.isEmpty) {
      setState(() => _errorText = 'Enter a category name.');
      return;
    }
    FocusScope.of(context).unfocus();
    Navigator.of(context).pop(categoryName);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        controller: _controller,
        focusNode: _focusNode,
        autofocus: true,
        textCapitalization: TextCapitalization.words,
        textInputAction: TextInputAction.done,
        decoration: InputDecoration(
          labelText: 'Category name',
          errorText: _errorText,
        ),
        onChanged: (_) {
          if (_errorText != null) setState(() => _errorText = null);
        },
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(onPressed: _submit, child: const Text('Save')),
      ],
    );
  }
}

class ManageFormFieldsPage extends StatefulWidget {
  const ManageFormFieldsPage({
    super.key,
    required this.section,
    required this.settings,
  });

  final WalletSection section;
  final StartupSettingsProvider settings;

  @override
  State<ManageFormFieldsPage> createState() => _ManageFormFieldsPageState();
}

class _ManageFormFieldsPageState extends State<ManageFormFieldsPage> {
  bool _isSaving = false;

  Future<void> _addCustomField() async {
    final field = await showDialog<CustomFieldSchema>(
      context: context,
      builder: (_) => const _CustomFieldDialog(),
    );
    if (!mounted || field == null) return;
    final existingFields = widget.settings.customFieldsFor(widget.section);
    final conflicts = existingFields.any(
      (item) => item.name.toLowerCase() == field.name.toLowerCase(),
    );
    if (conflicts) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('A field with this name already exists.')),
      );
      return;
    }
    await _saveCustomFields([...existingFields, field]);
  }

  Future<void> _deleteCustomField(CustomFieldSchema field) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete Custom Field?'),
        content: Text('Remove "${field.name}" from future add/edit forms?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (!mounted || confirmed != true) return;
    await _saveCustomFields(
      widget.settings
          .customFieldsFor(widget.section)
          .where((item) => item.name != field.name)
          .toList(),
    );
  }

  Future<void> _saveCustomFields(List<CustomFieldSchema> fields) async {
    if (_isSaving) return;
    setState(() => _isSaving = true);
    try {
      await widget.settings.saveCustomFields(widget.section, fields);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not save custom fields.')),
        );
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final customFields = widget.settings.customFieldsFor(widget.section);
    return Scaffold(
      appBar: AppBar(title: const Text('Manage Form Fields')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _isSaving ? null : _addCustomField,
        icon: const Icon(Icons.add),
        label: const Text('Add Custom Field'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
        children: [
          Text('CUSTOM FIELDS', style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: 8),
          Card(
            child: customFields.isEmpty
                ? const ListTile(
                    title: Text('No custom fields'),
                    subtitle: Text(
                      'Add fields for information unique to this section.',
                    ),
                  )
                : Column(
                    children: [
                      for (
                        var index = 0;
                        index < customFields.length;
                        index++
                      ) ...[
                        ListTile(
                          leading: const Icon(Icons.text_fields_rounded),
                          title: Text(customFields[index].name),
                          subtitle: Text(
                            _dataTypeLabel(customFields[index].dataType),
                          ),
                          trailing: IconButton(
                            tooltip: 'Delete custom field',
                            icon: const Icon(Icons.delete_outline),
                            onPressed: _isSaving
                                ? null
                                : () => _deleteCustomField(customFields[index]),
                          ),
                        ),
                        if (index != customFields.length - 1)
                          const Divider(height: 1),
                      ],
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  String _dataTypeLabel(CustomFieldDataType dataType) => switch (dataType) {
    CustomFieldDataType.text => 'Text',
    CustomFieldDataType.number => 'Number',
    CustomFieldDataType.date => 'Date',
  };
}

class _CustomFieldDialog extends StatefulWidget {
  const _CustomFieldDialog();

  @override
  State<_CustomFieldDialog> createState() => _CustomFieldDialogState();
}

class _CustomFieldDialogState extends State<_CustomFieldDialog> {
  late final TextEditingController _nameController;
  late final FocusNode _nameFocusNode;
  CustomFieldDataType _dataType = CustomFieldDataType.text;
  String? _errorText;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController();
    _nameFocusNode = FocusNode();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _nameFocusNode.requestFocus();
    });
  }

  @override
  void dispose() {
    _nameFocusNode.dispose();
    _nameController.dispose();
    super.dispose();
  }

  void _submit() {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      setState(() => _errorText = 'Enter a field name.');
      return;
    }
    FocusScope.of(context).unfocus();
    Navigator.of(
      context,
    ).pop(CustomFieldSchema(name: name, dataType: _dataType));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Add Custom Field'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _nameController,
            focusNode: _nameFocusNode,
            autofocus: true,
            textCapitalization: TextCapitalization.words,
            textInputAction: TextInputAction.done,
            decoration: InputDecoration(
              labelText: 'Field name',
              errorText: _errorText,
            ),
            onChanged: (_) {
              if (_errorText != null) setState(() => _errorText = null);
            },
            onSubmitted: (_) => _submit(),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<CustomFieldDataType>(
            initialValue: _dataType,
            decoration: const InputDecoration(labelText: 'Data type'),
            items: CustomFieldDataType.values
                .map(
                  (type) => DropdownMenuItem(
                    value: type,
                    child: Text(switch (type) {
                      CustomFieldDataType.text => 'Text',
                      CustomFieldDataType.number => 'Number',
                      CustomFieldDataType.date => 'Date',
                    }),
                  ),
                )
                .toList(),
            onChanged: (value) {
              if (value != null) setState(() => _dataType = value);
            },
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(onPressed: _submit, child: const Text('Add')),
      ],
    );
  }
}
