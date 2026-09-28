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
import java.time.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun ExpiryFieldInput(label:String,value:String,preferMonth:Boolean,change:(String)->Unit) {
 var open by remember {mutableStateOf(false)}
 OutlinedSelector(label,expiryInput(value).ifBlank {"Choose date"},{open=true}) {
  Row {
   IconButton(onClick={open=true}) {Icon(Icons.Outlined.CalendarMonth,"Choose date for "+label)}
   if(value.isNotBlank()) IconButton(onClick={change("")}) {Icon(Icons.Outlined.Clear,"Clear "+label)}
  }
 }
 if(open) CompositionLocalProvider(androidx.compose.runtime.saveable.LocalSaveableStateRegistry provides null) {
  val initial=expiryDateValue(value) ?: LocalDate.now()
  var monthly by remember {mutableStateOf(if(value.isBlank()) preferMonth else Regex("\\d{4}|\\d{2}/\\d{2}").matches(value))}
  var month by remember {mutableIntStateOf(initial.monthValue)}
  var year by remember {mutableStateOf(initial.year.toString())}
  val picker=rememberDatePickerState(initialSelectedDateMillis=initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),yearRange=minOf(1900,initial.year)..maxOf(2100,initial.year))
  DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(enabled=if(monthly) year.toIntOrNull() in 2000..2099 else picker.selectedDateMillis!=null,onClick={
   val result=if(monthly) "%02d/%02d".format(java.util.Locale.ROOT,month,year.toInt()%100)
    else Instant.ofEpochMilli(picker.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate().toString()
   change(result);open=false
  }) {Text("Set date")}},dismissButton={TextButton(onClick={open=false}) {Text("Cancel")}}) {
   Column(Modifier.verticalScroll(rememberScrollState())) {
    Text(label,Modifier.padding(16.dp),style=MaterialTheme.typography.titleLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
     listOf(true to "MM/YY",false to "YYYY-MM-DD").forEachIndexed {index,(isMonth,title)->
      SegmentedButton(selected=monthly==isMonth,onClick={
       if(monthly && !isMonth) year.toIntOrNull()?.takeIf {it in 1900..2100}?.let {
        picker.selectedDateMillis=YearMonth.of(it,month).atEndOfMonth().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
       }
       if(!monthly && isMonth) picker.selectedDateMillis?.let {val date=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC);month=date.monthValue;year=date.year.toString()}
       monthly=isMonth
      },shape=SegmentedButtonDefaults.itemShape(index,2)) {Text(title)}
     }
    }
    if(monthly) Column(Modifier.padding(16.dp)) {
     OutlinedTextField(year,{year=it},label={Text("Year")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=year.toIntOrNull() !in 2000..2099)
     FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      (1..12).forEach {m->FilterChip(month==m,{month=m},label={Text(Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault()))})}
     }
     Text("Valid through the end of the selected month.",style=MaterialTheme.typography.bodySmall)
    } else DatePicker(picker,showModeToggle=true)
   }
  }
 }
}
