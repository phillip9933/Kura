import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';

enum PassSearchStyle { alwaysOn, icon }

enum SearchBarPosition { top, bottom }

enum ControlRowPosition { top, bottom }

enum PassGridDisplayMode { front, back, virtualCards }

enum BarcodeOrientation { defaultOrientation, flipped }

enum WalletSection { payments, passes, identity }

enum CustomFieldDataType { text, number, date }

class CustomFieldSchema {
  const CustomFieldSchema({required this.name, required this.dataType});

  final String name;
  final CustomFieldDataType dataType;

  Map<String, String> toMap() => {'name': name, 'dataType': dataType.name};

  factory CustomFieldSchema.fromMap(Map<String, dynamic> map) {
    return CustomFieldSchema(
      name: map['name']?.toString() ?? '',
      dataType: CustomFieldDataType.values.firstWhere(
        (type) => type.name == map['dataType'],
        orElse: () => CustomFieldDataType.text,
      ),
    );
  }
}

class StartupSettingsProvider with ChangeNotifier {
  bool _showAuthenticationScreen = true;
  int _defaultScreenIndex = 0;
  String _selectedCurrencyCode = 'EUR';
  String _selectedCurrencySymbol = '€';
  bool _showPaymentsTab = true;
  bool _showPassesTab = true;
  bool _showIdentityTab = true;
  bool _showBottomNavigationBar = true;
  bool _isQrImportScannerEnabled = true;
  bool _showPassQrButton = true;
  bool _isPassSearchEnabled = true;
  bool _isExpiryNotificationEnabled = true;
  int _expiryNotificationLeadMonths = 2;
  bool _maxBrightnessOnBarcodeView = false;
  BarcodeOrientation _defaultBarcodeOrientation =
      BarcodeOrientation.defaultOrientation;
  PassSearchStyle _passSearchStyle = PassSearchStyle.alwaysOn;
  SearchBarPosition _searchBarPosition = SearchBarPosition.top;
  ControlRowPosition _controlRowPosition = ControlRowPosition.top;
  PassGridDisplayMode _passGridDisplayMode = PassGridDisplayMode.front;
  int _passGridColumns = 1;
  final Map<WalletSection, int> _sectionGridColumns = {
    WalletSection.payments: 1,
    WalletSection.passes: 1,
    WalletSection.identity: 1,
  };
  final Map<WalletSection, PassGridDisplayMode> _sectionGridDisplayModes = {
    WalletSection.payments: PassGridDisplayMode.front,
    WalletSection.passes: PassGridDisplayMode.front,
    WalletSection.identity: PassGridDisplayMode.front,
  };
  final Map<WalletSection, List<String>> _sectionCategories = {
    WalletSection.payments: [
      'Visa',
      'Mastercard',
      'RuPay',
      'American Express',
      'Discover',
    ],
    WalletSection.passes: [
      'Retail',
      'Tickets & Transit',
      'Access',
      'Health',
      'Identity',
      'Generic',
    ],
    WalletSection.identity: [
      'Passport',
      'Driver License',
      'National ID',
      'Health Card',
      'Other',
    ],
  };
  static const Map<WalletSection, List<String>> _defaultSectionCategories = {
    WalletSection.payments: [
      'Visa',
      'Mastercard',
      'RuPay',
      'American Express',
      'Discover',
    ],
    WalletSection.passes: [
      'Retail',
      'Tickets & Transit',
      'Access',
      'Health',
      'Identity',
      'Generic',
    ],
    WalletSection.identity: [
      'Passport',
      'Driver License',
      'National ID',
      'Health Card',
      'Other',
    ],
  };
  static const List<String> _legacyPassCategorySeed = [
    'Loyalty',
    'Gift Cards',
    'Events',
    'Boarding',
    'Campus',
    'Corporate',
    'Hotel',
    'Other',
  ];
  final Map<WalletSection, List<CustomFieldSchema>> _customFieldSchemas = {
    for (final section in WalletSection.values) section: [],
  };

  static const String _authKey = 'showAuthenticationScreen';
  static const String _defaultScreenKey = 'defaultScreenIndex';
  static const String _currencyCodeKey = 'selectedCurrencyCode';
  static const String _currencySymbolKey = 'selectedCurrencySymbol';
  static const String _showPaymentsTabKey = 'showPaymentsTab';
  static const String _showPassesTabKey = 'showPassesTab';
  static const String _showIdentityTabKey = 'showIdentityTab';
  static const String _showBottomNavigationBarKey = 'showBottomNavigationBar';
  static const String _isQrImportScannerEnabledKey = 'isQrImportScannerEnabled';
  static const String _showPassQrButtonKey = 'showPassQrButton';
  static const String _isPassSearchEnabledKey = 'isPassSearchEnabled';
  static const String _isExpiryNotificationEnabledKey =
      'isExpiryNotificationEnabled';
  static const String _expiryNotificationLeadMonthsKey =
      'expiryNotificationLeadMonths';
  static const String _passSearchStyleKey = 'passSearchStyle';
  static const String _searchBarPositionKey = 'searchBarPosition';
  static const String _controlRowPositionKey = 'controlRowPosition';
  static const String _passGridDisplayModeKey = 'passGridDisplayMode';
  static const String _passGridColumnsKey = 'passGridColumns';
  static const String _maxBrightnessOnBarcodeViewKey =
      'maxBrightnessOnBarcodeView';
  static const String _defaultBarcodeOrientationKey =
      'defaultBarcodeOrientation';

  bool get showAuthenticationScreen => _showAuthenticationScreen;
  int get defaultScreenIndex => _defaultScreenIndex;
  String get selectedCurrencyCode => _selectedCurrencyCode;
  String get selectedCurrencySymbol => _selectedCurrencySymbol;
  bool get showPaymentsTab => _showPaymentsTab;
  bool get showPassesTab => _showPassesTab;
  bool get showIdentityTab => _showIdentityTab;
  bool get showBottomNavigationBar => _showBottomNavigationBar;
  bool get isQrImportScannerEnabled => _isQrImportScannerEnabled;
  bool get showPassQrButton => _showPassQrButton;
  bool get isPassSearchEnabled => _isPassSearchEnabled;
  bool get isExpiryNotificationEnabled => _isExpiryNotificationEnabled;
  int get expiryNotificationLeadMonths => _expiryNotificationLeadMonths;
  bool get maxBrightnessOnBarcodeView => _maxBrightnessOnBarcodeView;
  BarcodeOrientation get defaultBarcodeOrientation =>
      _defaultBarcodeOrientation;
  PassSearchStyle get passSearchStyle => _passSearchStyle;
  SearchBarPosition get searchBarPosition => _searchBarPosition;
  ControlRowPosition get controlRowPosition => _controlRowPosition;
  PassGridDisplayMode get passGridDisplayMode => _passGridDisplayMode;
  int get passGridColumns => _passGridColumns;

  int gridColumnsFor(WalletSection section) => _sectionGridColumns[section]!;
  PassGridDisplayMode gridModeFor(WalletSection section) =>
      _sectionGridDisplayModes[section]!;
  List<String> categoriesFor(WalletSection section) =>
      List.unmodifiable(_sectionCategories[section]!);
  List<String> defaultCategoriesFor(WalletSection section) =>
      List.unmodifiable(_defaultSectionCategories[section]!);
  List<CustomFieldSchema> customFieldsFor(WalletSection section) =>
      List.unmodifiable(_customFieldSchemas[section]!);

  static const List<Map<String, String>> majorCurrencies = [
    {'code': 'AUD', 'symbol': 'A\$', 'name': 'Australian Dollar'},
    {'code': 'BDT', 'symbol': '৳', 'name': 'Bangladeshi Taka'},
    {'code': 'GBP', 'symbol': '£', 'name': 'British Pound'},
    {'code': 'CAD', 'symbol': 'C\$', 'name': 'Canadian Dollar'},
    {'code': 'CNY', 'symbol': '¥', 'name': 'Chinese Yuan'},
    {'code': 'EUR', 'symbol': '€', 'name': 'Euro'},
    {'code': 'INR', 'symbol': '₹', 'name': 'Indian Rupee'},
    {'code': 'JPY', 'symbol': '¥', 'name': 'Japanese Yen'},
    {'code': 'NZD', 'symbol': 'NZ\$', 'name': 'New Zealand Dollar'},
    {'code': 'SEK', 'symbol': 'kr', 'name': 'Swedish Krona'},
    {'code': 'CHF', 'symbol': 'CHF', 'name': 'Swiss Franc'},
    {'code': 'USD', 'symbol': '\$', 'name': 'US Dollar'},
  ];

  // Initialize settings from saved preferences
  Future<void> loadStartupSettings() async {
    final prefs = await SharedPreferences.getInstance();
    _showAuthenticationScreen = prefs.getBool(_authKey) ?? true;
    _defaultScreenIndex = prefs.getInt(_defaultScreenKey) ?? 0;
    _selectedCurrencyCode = prefs.getString(_currencyCodeKey) ?? 'EUR';
    _selectedCurrencySymbol = prefs.getString(_currencySymbolKey) ?? '€';
    _showPaymentsTab = prefs.getBool(_showPaymentsTabKey) ?? true;
    _showPassesTab = prefs.getBool(_showPassesTabKey) ?? true;
    _showIdentityTab = prefs.getBool(_showIdentityTabKey) ?? true;
    _showBottomNavigationBar =
        prefs.getBool(_showBottomNavigationBarKey) ?? true;
    _isQrImportScannerEnabled =
        prefs.getBool(_isQrImportScannerEnabledKey) ?? true;
    if (!_showPaymentsTab && !_showPassesTab && !_showIdentityTab) {
      _showPaymentsTab = true;
      await prefs.setBool(_showPaymentsTabKey, true);
    }
    if (!_isTabVisible(_defaultScreenIndex)) {
      _defaultScreenIndex = firstVisibleTabIndex;
      await prefs.setInt(_defaultScreenKey, _defaultScreenIndex);
    }
    _showPassQrButton = prefs.getBool(_showPassQrButtonKey) ?? true;
    _isPassSearchEnabled = prefs.getBool(_isPassSearchEnabledKey) ?? true;
    _isExpiryNotificationEnabled =
        prefs.getBool(_isExpiryNotificationEnabledKey) ?? true;
    _expiryNotificationLeadMonths =
        prefs.getInt(_expiryNotificationLeadMonthsKey) ?? 2;
    _maxBrightnessOnBarcodeView =
        prefs.getBool(_maxBrightnessOnBarcodeViewKey) ?? false;
    _defaultBarcodeOrientation = BarcodeOrientation.values.firstWhere(
      (orientation) =>
          orientation.name == prefs.getString(_defaultBarcodeOrientationKey),
      orElse: () => BarcodeOrientation.defaultOrientation,
    );
    final savedSearchStyle = prefs.getString(_passSearchStyleKey);
    _passSearchStyle = PassSearchStyle.values.firstWhere(
      (style) => style.name == savedSearchStyle,
      orElse: () => PassSearchStyle.alwaysOn,
    );
    _searchBarPosition = SearchBarPosition.values.firstWhere(
      (position) => position.name == prefs.getString(_searchBarPositionKey),
      orElse: () => SearchBarPosition.top,
    );
    _controlRowPosition = ControlRowPosition.values.firstWhere(
      (position) => position.name == prefs.getString(_controlRowPositionKey),
      orElse: () => ControlRowPosition.top,
    );
    final savedGridDisplayMode = prefs.getString(_passGridDisplayModeKey);
    _passGridDisplayMode = PassGridDisplayMode.values.firstWhere(
      (mode) => mode.name == savedGridDisplayMode,
      orElse: () => PassGridDisplayMode.front,
    );
    final savedGridColumns = prefs.getInt(_passGridColumnsKey) ?? 1;
    for (final section in WalletSection.values) {
      final savedColumns = prefs.getInt(_sectionGridColumnsKey(section));
      _sectionGridColumns[section] = [1, 2, 3].contains(savedColumns)
          ? savedColumns!
          : (section == WalletSection.passes &&
                    [1, 2, 3].contains(savedGridColumns)
                ? savedGridColumns
                : 1);
      final savedDisplayMode = prefs.getString(
        _sectionGridDisplayModeKey(section),
      );
      _sectionGridDisplayModes[section] = PassGridDisplayMode.values.firstWhere(
        (mode) => mode.name == savedDisplayMode,
        orElse: () => section == WalletSection.passes
            ? _passGridDisplayMode
            : PassGridDisplayMode.front,
      );
      final savedCategories = prefs.getStringList(
        _sectionCategoriesKey(section),
      );
      if (savedCategories != null && savedCategories.isNotEmpty) {
        final shouldMigratePassCategories =
            section == WalletSection.passes &&
            _listsEqual(savedCategories, _legacyPassCategorySeed);
        _sectionCategories[section] = shouldMigratePassCategories
            ? List.of(_defaultSectionCategories[WalletSection.passes]!)
            : savedCategories;
        if (shouldMigratePassCategories) {
          await prefs.setStringList(
            _sectionCategoriesKey(section),
            _sectionCategories[section]!,
          );
        }
      }
      final savedCustomFields = prefs.getString(
        _customFieldSchemasKey(section),
      );
      if (savedCustomFields != null) {
        final values = List<dynamic>.from(jsonDecode(savedCustomFields));
        _customFieldSchemas[section] = values
            .whereType<Map>()
            .map(
              (value) =>
                  CustomFieldSchema.fromMap(Map<String, dynamic>.from(value)),
            )
            .where((field) => field.name.isNotEmpty)
            .toList();
      }
    }
    _passGridColumns = [1, 2, 3].contains(savedGridColumns)
        ? savedGridColumns
        : 1;
    notifyListeners();
  }

  Future<void> toggleAuthenticationScreen() async {
    _showAuthenticationScreen = !_showAuthenticationScreen;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_authKey, _showAuthenticationScreen);
    notifyListeners();
  }

  Future<void> setDefaultScreen(int index) async {
    _defaultScreenIndex = _isTabVisible(index) ? index : firstVisibleTabIndex;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_defaultScreenKey, _defaultScreenIndex);
    notifyListeners();
  }

  bool get hasMultipleVisibleTabs =>
      [
        _showPaymentsTab,
        _showPassesTab,
        _showIdentityTab,
      ].where((visible) => visible).length >
      1;

  int get firstVisibleTabIndex {
    if (_showPaymentsTab) return 0;
    if (_showPassesTab) return 1;
    return 2;
  }

  bool isTabVisible(int index) => _isTabVisible(index);

  bool _isTabVisible(int index) {
    return switch (index) {
      0 => _showPaymentsTab,
      1 => _showPassesTab,
      2 => _showIdentityTab,
      _ => false,
    };
  }

  Future<void> setTabVisibility(int index, bool visible) async {
    if (!visible && !hasMultipleVisibleTabs) return;
    switch (index) {
      case 0:
        _showPaymentsTab = visible;
      case 1:
        _showPassesTab = visible;
      case 2:
        _showIdentityTab = visible;
      default:
        return;
    }
    if (!_isTabVisible(_defaultScreenIndex)) {
      _defaultScreenIndex = firstVisibleTabIndex;
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_showPaymentsTabKey, _showPaymentsTab);
    await prefs.setBool(_showPassesTabKey, _showPassesTab);
    await prefs.setBool(_showIdentityTabKey, _showIdentityTab);
    await prefs.setInt(_defaultScreenKey, _defaultScreenIndex);
    notifyListeners();
  }

  Future<void> setShowBottomNavigationBar(bool value) async {
    _showBottomNavigationBar = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_showBottomNavigationBarKey, value);
    notifyListeners();
  }

  Future<void> setCurrency(String code, String symbol) async {
    _selectedCurrencyCode = code;
    _selectedCurrencySymbol = symbol;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_currencyCodeKey, _selectedCurrencyCode);
    await prefs.setString(_currencySymbolKey, _selectedCurrencySymbol);
    notifyListeners();
  }

  Future<void> setQrImportScannerEnabled(bool value) async {
    _isQrImportScannerEnabled = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_isQrImportScannerEnabledKey, value);
    notifyListeners();
  }

  Future<void> setShowPassQrButton(bool value) async {
    _showPassQrButton = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_showPassQrButtonKey, value);
    notifyListeners();
  }

  Future<void> setPassSearchEnabled(bool value) async {
    _isPassSearchEnabled = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_isPassSearchEnabledKey, value);
    notifyListeners();
  }

  Future<void> setExpiryNotificationEnabled(bool value) async {
    _isExpiryNotificationEnabled = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_isExpiryNotificationEnabledKey, value);
    notifyListeners();
  }

  Future<void> setExpiryNotificationLeadMonths(int value) async {
    if (value < 1) return;
    _expiryNotificationLeadMonths = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_expiryNotificationLeadMonthsKey, value);
    notifyListeners();
  }

  Future<void> setMaxBrightnessOnBarcodeView(bool value) async {
    _maxBrightnessOnBarcodeView = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_maxBrightnessOnBarcodeViewKey, value);
    notifyListeners();
  }

  Future<void> setDefaultBarcodeOrientation(
    BarcodeOrientation orientation,
  ) async {
    _defaultBarcodeOrientation = orientation;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_defaultBarcodeOrientationKey, orientation.name);
    notifyListeners();
  }

  Future<void> setPassSearchStyle(PassSearchStyle value) async {
    _passSearchStyle = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_passSearchStyleKey, value.name);
    notifyListeners();
  }

  Future<void> setSearchBarPosition(SearchBarPosition value) async {
    _searchBarPosition = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_searchBarPositionKey, value.name);
    notifyListeners();
  }

  Future<void> setControlRowPosition(ControlRowPosition value) async {
    _controlRowPosition = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_controlRowPositionKey, value.name);
    notifyListeners();
  }

  Future<void> setPassGridDisplayMode(PassGridDisplayMode value) async {
    _passGridDisplayMode = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_passGridDisplayModeKey, value.name);
    notifyListeners();
  }

  Future<void> setPassGridColumns(int value) async {
    if (![1, 2, 3].contains(value)) return;
    _passGridColumns = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_passGridColumnsKey, value);
    notifyListeners();
  }

  String _sectionGridColumnsKey(WalletSection section) =>
      '${section.name}GridColumns';
  String _sectionGridDisplayModeKey(WalletSection section) =>
      '${section.name}GridDisplayMode';
  String _sectionCategoriesKey(WalletSection section) =>
      '${section.name}Categories';
  String _customFieldSchemasKey(WalletSection section) =>
      '${section.name}CustomFieldSchemas';

  Future<void> setGridColumns(WalletSection section, int value) async {
    if (![1, 2, 3].contains(value)) return;
    _sectionGridColumns[section] = value;
    if (section == WalletSection.passes) _passGridColumns = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_sectionGridColumnsKey(section), value);
    notifyListeners();
  }

  Future<void> setGridDisplayMode(
    WalletSection section,
    PassGridDisplayMode value,
  ) async {
    _sectionGridDisplayModes[section] = value;
    if (section == WalletSection.passes) _passGridDisplayMode = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_sectionGridDisplayModeKey(section), value.name);
    notifyListeners();
  }

  Future<void> saveCategories(
    WalletSection section,
    List<String> categories,
  ) async {
    final cleaned = categories
        .map((category) => category.trim())
        .where((category) => category.isNotEmpty)
        .toList();
    _sectionCategories[section] = cleaned;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(_sectionCategoriesKey(section), cleaned);
    notifyListeners();
  }

  Future<void> restoreDefaultCategories(WalletSection section) =>
      saveCategories(section, defaultCategoriesFor(section));

  bool _listsEqual(List<String> first, List<String> second) {
    if (first.length != second.length) return false;
    for (var index = 0; index < first.length; index++) {
      if (first[index] != second[index]) return false;
    }
    return true;
  }

  Future<void> saveCustomFields(
    WalletSection section,
    List<CustomFieldSchema> fields,
  ) async {
    _customFieldSchemas[section] = List.of(fields);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _customFieldSchemasKey(section),
      jsonEncode(fields.map((field) => field.toMap()).toList()),
    );
    notifyListeners();
  }
}
