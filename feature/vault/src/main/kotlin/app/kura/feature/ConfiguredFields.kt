package app.kura.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField

val customFieldTypes=listOf("text","number","currency","date")
val customDateFormats=listOf("MM/yy","dd/MM/yy","yyyy-MM-dd")
private val shortDate=DateTimeFormatterBuilder().appendPattern("dd/MM/").appendValueReduced(ChronoField.YEAR,2,2,2000).toFormatter().withResolverStyle(ResolverStyle.STRICT)
fun customDate(value:String):LocalDate? = runCatching {LocalDate.parse(value)}.getOrNull()
    ?: runCatching {LocalDate.parse(value,shortDate)}.getOrNull()
fun validCustomValue(field:CustomField,value:String):Boolean {
    if(value.isBlank()) return true
    return when(field.dataType) {
        "number","currency"->value.toBigDecimalOrNull()!=null
        "date"->if(field.dateFormat=="MM/yy") runCatching {
            require(Regex("\\d{2}/\\d{2}").matches(value))
            YearMonth.of(2000+value.takeLast(2).toInt(),value.take(2).toInt())
        }.isSuccess || customDate(value)!=null else customDate(value)!=null
        else->true
    }
}
fun customTypeLabel(field:CustomField)=when(field.dataType) {
    "date"->"Date ("+when(field.dateFormat) {"MM/yy"->"MM/YY";"dd/MM/yy"->"DD/MM/YY";else->"YYYY-MM-DD"}+")"
    else->field.dataType.replaceFirstChar {it.uppercase()}
}
fun customDisplayValue(field:CustomField?,value:String,currency:String):String=when(field?.dataType) {
    "currency"->moneyValue(value,currency)
    "date"->customDate(value)?.format(if(field.dateFormat=="dd/MM/yy") shortDate else DateTimeFormatter.ofPattern(field.dateFormat)) ?: value
    else->value
}

@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable
internal fun ConfiguredFieldInput(field:CustomField,value:String,currency:String,change:(String)->Unit) {
    var datePicker by remember {mutableStateOf(false)}
    var monthPicker by remember {mutableStateOf(false)}
    val isDate=field.dataType=="date"
    fun openDate() {if(field.dateFormat=="MM/yy") monthPicker=true else datePicker=true}
    if(isDate) OutlinedSelector(field.name,customDisplayValue(field,value,currency).ifBlank {"Choose date"},::openDate) {
        Row {
            IconButton(onClick=::openDate) {Icon(Icons.Outlined.CalendarMonth,"Choose date for "+field.name)}
            if(value.isNotBlank()) IconButton(onClick={change("")}) {Icon(Icons.Outlined.Clear,"Clear "+field.name)}
        }
    } else OutlinedTextField(value,change,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),label={Text(field.name)},modifier=Modifier.fillMaxWidth(),singleLine=true,
        isError=!validCustomValue(field,value),suffix=if(field.dataType=="currency") ({Text(currency)}) else null,
        keyboardOptions=KeyboardOptions(keyboardType=if(field.dataType in setOf("currency","number")) KeyboardType.Decimal else KeyboardType.Text))
    if(datePicker) androidx.compose.runtime.CompositionLocalProvider(androidx.compose.runtime.saveable.LocalSaveableStateRegistry provides null) {
        // Selection remains in memory, never in Activity saved-state parcels.
        val initial=customDate(value) ?: LocalDate.now()
        val picker=rememberDatePickerState(initialSelectedDateMillis=initial.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),yearRange=minOf(1900,initial.year)..maxOf(2100,initial.year))
        DatePickerDialog(onDismissRequest={datePicker=false},
            confirmButton={TextButton(enabled=picker.selectedDateMillis!=null,onClick={
                val date=java.time.Instant.ofEpochMilli(picker.selectedDateMillis!!).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                change(date.toString());datePicker=false
            }) {Text("Set date")}},dismissButton={TextButton(onClick={datePicker=false}) {Text("Cancel")}}) {DatePicker(picker)}
    }
    if(monthPicker) {
        val initial=customDate(value)?.let {YearMonth.from(it)} ?: runCatching {YearMonth.of(2000+value.takeLast(2).toInt(),value.take(2).toInt())}.getOrDefault(YearMonth.now())
        var month by remember {mutableIntStateOf(initial.monthValue)}
        var year by remember {mutableStateOf(initial.year.toString())}
        AlertDialog(onDismissRequest={monthPicker=false},title={Text("Choose month and year")},
            text={Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(year,{year=it},label={Text("Year")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (1..12).forEach {m->FilterChip(month==m,{month=m},label={Text(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault()))})}
                }
            }},confirmButton={TextButton(enabled=year.toIntOrNull() in 1..9999,onClick={
                change(YearMonth.of(year.toInt(),month).atDay(1).toString());monthPicker=false
            }) {Text("Set date")}},dismissButton={TextButton(onClick={monthPicker=false}) {Text("Cancel")}})
    }
}

/** Explicit contrast for the unselected track/border in the neutral Kura palette. */
@Composable
internal fun KuraSwitch(checked:Boolean,onCheckedChange:(Boolean)->Unit,modifier:Modifier=Modifier) {
    val scheme=MaterialTheme.colorScheme
    Switch(checked,onCheckedChange,modifier,colors=SwitchDefaults.colors(
        checkedThumbColor=scheme.onPrimary,checkedTrackColor=scheme.primary,
        uncheckedThumbColor=scheme.onSurface,uncheckedTrackColor=scheme.surfaceVariant,
        uncheckedBorderColor=scheme.onSurfaceVariant))
}
@Composable
internal fun CategorySettings(prefs:VaultPreferences,table:String,set:(String,Any)->Unit,rename:(String)->Unit,edit:()->Unit) {
    SettingsGroup("Categories") {
        Text("Tap a category to rename it and its items.",Modifier.padding(16.dp))
        val categories=prefs.categories(table)
        fun persist(order:List<String>) {set(sectionKey(table)+"Categories",org.json.JSONArray(order).toString())}
        SettingsRow("Sort A–Z","Sort categories alphabetically",Icons.Outlined.SortByAlpha,{
            val collator=java.text.Collator.getInstance().apply {strength=java.text.Collator.PRIMARY}
            persist(categories.sortedWith(compareBy(collator) {it}))
        })
        categories.forEachIndexed {index,name->SettingsRow(name,"Rename category",Icons.Outlined.Category,{rename(name)},
            trailing={Row {
                IconButton(enabled=index>0,onClick={persist(categories.toMutableList().apply {add(index-1,removeAt(index))})}) {Icon(Icons.Outlined.ArrowUpward,"Move "+name+" up")}
                IconButton(enabled=index<categories.lastIndex,onClick={persist(categories.toMutableList().apply {add(index+1,removeAt(index))})}) {Icon(Icons.Outlined.ArrowDownward,"Move "+name+" down")}
            }})}
        SettingsRow("Edit Categories","Add, remove, or reorder categories",Icons.Outlined.Edit,edit)
        SettingsRow("Restore Default Categories","Restore the default category list",Icons.Outlined.Restore,{
            set(sectionKey(table)+"Categories",org.json.JSONArray(defaultCategories.getValue(table)).toString())
        })
    }
}
