import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kura/models/startup_settings_provider.dart';

/// A single editor for globally configured fields and card-specific fields.
class ConfiguredCustomFields extends StatefulWidget {
  const ConfiguredCustomFields({
    super.key,
    required this.schemas,
    required this.controllers,
    required this.localFieldTypes,
  });

  final List<CustomFieldSchema> schemas;
  final Map<String, TextEditingController> controllers;
  final Map<String, CustomFieldDataType> localFieldTypes;

  @override
  State<ConfiguredCustomFields> createState() => _ConfiguredCustomFieldsState();
}

class _ConfiguredCustomFieldsState extends State<ConfiguredCustomFields> {
  Set<String> get _configuredNames =>
      widget.schemas.map((schema) => schema.name).toSet();

  List<String> get _localNames => widget.controllers.keys
      .where((name) => !_configuredNames.contains(name))
      .toList(growable: false);

  Future<void> _addLocalField() async {
    final result = await showDialog<_LocalFieldData>(
      context: context,
      builder: (context) =>
          _LocalFieldDialog(existingNames: widget.controllers.keys.toSet()),
    );
    if (result == null || !mounted) return;
    setState(() {
      widget.controllers[result.name] = TextEditingController(
        text: result.value,
      );
      widget.localFieldTypes[result.name] = result.dataType;
    });
  }

  Future<void> _removeLocalField(String name) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Remove local field?'),
        content: Text(
          'Remove "$name" and its saved value? This information will be lost when you save.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(dialogContext).colorScheme.error,
              foregroundColor: Theme.of(dialogContext).colorScheme.onError,
            ),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Remove'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    final controller = widget.controllers[name];
    setState(() {
      widget.controllers.remove(name);
      widget.localFieldTypes.remove(name);
    });
    // TextFormField still depends on its controller until this frame finishes.
    WidgetsBinding.instance.addPostFrameCallback((_) => controller?.dispose());
  }

  @override
  Widget build(BuildContext context) {
    for (final schema in widget.schemas) {
      widget.controllers.putIfAbsent(schema.name, TextEditingController.new);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'ADDITIONAL INFORMATION',
          style: Theme.of(context).textTheme.labelLarge,
        ),
        const SizedBox(height: 12),
        for (final schema in widget.schemas) ...[
          _ConfiguredField(
            schema: schema,
            controller: widget.controllers[schema.name]!,
          ),
          const SizedBox(height: 16),
        ],
        for (final name in _localNames) ...[
          Row(
            children: [
              Expanded(
                child: _ConfiguredField(
                  schema: CustomFieldSchema(
                    name: name,
                    dataType:
                        widget.localFieldTypes[name] ??
                        CustomFieldDataType.text,
                  ),
                  controller: widget.controllers[name]!,
                ),
              ),
              IconButton(
                tooltip: 'Remove $name',
                onPressed: () => _removeLocalField(name),
                icon: const Icon(Icons.remove_circle_outline),
              ),
            ],
          ),
          const SizedBox(height: 16),
        ],
        OutlinedButton.icon(
          onPressed: _addLocalField,
          icon: const Icon(Icons.add_rounded),
          label: const Text('Add local field'),
        ),
      ],
    );
  }
}

class _LocalFieldData {
  const _LocalFieldData({
    required this.name,
    required this.value,
    required this.dataType,
  });

  final String name;
  final String value;
  final CustomFieldDataType dataType;
}

class _LocalFieldDialog extends StatefulWidget {
  const _LocalFieldDialog({required this.existingNames});

  final Set<String> existingNames;

  @override
  State<_LocalFieldDialog> createState() => _LocalFieldDialogState();
}

class _LocalFieldDialogState extends State<_LocalFieldDialog> {
  final _nameController = TextEditingController();
  final _valueController = TextEditingController();
  CustomFieldDataType _dataType = CustomFieldDataType.text;
  String? _errorText;

  @override
  void dispose() {
    _nameController.dispose();
    _valueController.dispose();
    super.dispose();
  }

  void _submit() {
    final name = _nameController.text.trim();
    final nameExists = widget.existingNames.any(
      (existingName) => existingName.toLowerCase() == name.toLowerCase(),
    );
    if (name.isEmpty || nameExists) {
      setState(() {
        _errorText = name.isEmpty
            ? 'Enter a field name.'
            : 'A field with this name already exists.';
      });
      return;
    }
    Navigator.pop(
      context,
      _LocalFieldData(
        name: name,
        value: _valueController.text.trim(),
        dataType: _dataType,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Add local field'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _nameController,
            autofocus: true,
            decoration: InputDecoration(
              labelText: 'Field name',
              errorText: _errorText,
            ),
            onChanged: (_) {
              if (_errorText != null) setState(() => _errorText = null);
            },
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<CustomFieldDataType>(
            initialValue: _dataType,
            decoration: const InputDecoration(labelText: 'Field type'),
            items: CustomFieldDataType.values
                .map(
                  (type) => DropdownMenuItem(
                    value: type,
                    child: Text(
                      type.name[0].toUpperCase() + type.name.substring(1),
                    ),
                  ),
                )
                .toList(),
            onChanged: (type) {
              if (type != null) setState(() => _dataType = type);
            },
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _valueController,
            keyboardType: _dataType == CustomFieldDataType.number
                ? TextInputType.number
                : TextInputType.text,
            inputFormatters: _dataType == CustomFieldDataType.number
                ? [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))]
                : null,
            decoration: const InputDecoration(labelText: 'Value'),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(onPressed: _submit, child: const Text('Add')),
      ],
    );
  }
}

class _ConfiguredField extends StatelessWidget {
  const _ConfiguredField({required this.schema, required this.controller});

  final CustomFieldSchema schema;
  final TextEditingController controller;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      keyboardType: schema.dataType == CustomFieldDataType.number
          ? TextInputType.number
          : schema.dataType == CustomFieldDataType.date
          ? TextInputType.datetime
          : TextInputType.text,
      inputFormatters: schema.dataType == CustomFieldDataType.number
          ? [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))]
          : null,
      readOnly: schema.dataType == CustomFieldDataType.date,
      onTap: schema.dataType != CustomFieldDataType.date
          ? null
          : () async {
              final selected = await showDatePicker(
                context: context,
                firstDate: DateTime(1900),
                lastDate: DateTime(2100),
                initialDate:
                    DateTime.tryParse(controller.text) ?? DateTime.now(),
              );
              if (selected != null) {
                controller.text =
                    '${selected.year.toString().padLeft(4, '0')}-${selected.month.toString().padLeft(2, '0')}-${selected.day.toString().padLeft(2, '0')}';
              }
            },
      decoration: InputDecoration(
        labelText: schema.name,
        suffixIcon: schema.dataType == CustomFieldDataType.date
            ? const Icon(Icons.calendar_today_outlined)
            : null,
      ),
    );
  }
}
