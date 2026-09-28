package app.kura.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.time.*

/** Read-only selector with the same floating outline label as editable fields. */
@Composable internal fun OutlinedSelector(label:String,value:String,choose:()->Unit,trailing:@Composable ()->Unit) {
 Box {
  OutlinedTextField(value,{},readOnly=true,label={Text(label)},shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth(),trailingIcon=trailing)
  Box(Modifier.matchParentSize().padding(end=88.dp).clickable(onClick=choose))
 }
}

fun defaultFormCategory(section:String,prefs:VaultPreferences):String {
 val desired=when(section) {"wallets"->"Credit";"identities"->"Passport";else->"Other"}
 return prefs.categories(section).firstOrNull {it.equals(desired,true)} ?: prefs.categories(section).firstOrNull().orEmpty()
}
fun suggestedFields(section:String,category:String):List<CustomField> {
 fun text(name:String)=CustomField(name,"text")
 fun date(name:String)=CustomField(name,"date","yyyy-MM-dd")
 return when(section) {
  "wallets"->when(category.lowercase()) {
   "gift card"->listOf(text("Recipient"),CustomField("Balance","currency"),date("Purchase date"))
   "loyalty","store card"->listOf(text("Member name"),text("Member number"),text("Tier"),CustomField("Points","number"))
   "gym"->listOf(text("Member number"),text("Member name"),text("Home club"),text("Plan"),date("Member since"))
   "membership"->listOf(text("Member number"),text("Member name"),text("Membership level"),date("Member since"))
   "library"->listOf(text("Borrower number"),text("Borrower name"),text("Library branch"))
   "prepaid"->listOf(text("Cardholder"),CustomField("Balance","currency"))
   "credit","debit"->listOf(text("Cardholder"))
   else->emptyList()
  }
  "identities"->when(category.lowercase()) {
   "passport"->listOf(date("Date of birth"),text("Nationality"),text("Issuing country"),date("Issue date"))
   "driver's license"->listOf(date("Date of birth"),text("License class"),text("Issuing authority"),date("Issue date"))
   "residence card"->listOf(text("Nationality"),text("Residence status"),text("Issuing country"),date("Issue date"))
   "national id"->listOf(date("Date of birth"),text("Issuing country"),date("Issue date"))
   "health insurance"->listOf(text("Insurer"),text("Policy number"),text("Group number"))
   "employee id"->listOf(text("Employer"),text("Department"),text("Job title"))
   "student id"->listOf(text("Institution"),text("Course / program"),text("Academic year"))
   else->emptyList()
  }
  else->when(category.lowercase()) {
   "boarding pass"->listOf(text("Passenger"),text("From"),text("To"),text("Flight number"),text("Terminal"),text("Gate"),text("Seat"),text("Travel class"),text("Booking reference"))
   "transit"->listOf(text("From"),text("To"),text("Service number"),text("Zone"),text("Ticket class"))
   "concert","sports","event","event ticket"->listOf(text("Venue"),text("Section"),text("Row"),text("Seat"),text("Booking reference"))
   "reservation"->listOf(text("Location"),text("Guest name"),text("Booking reference"),CustomField("Guests","number"),date("Check-out date"))
   "parking"->listOf(text("Vehicle registration"),text("Location"),text("Space"))
   "coupon"->listOf(text("Offer"),CustomField("Minimum spend","currency"),text("Redemption code"))
   "temporary access"->listOf(text("Visitor name"),text("Location"),text("Host"),date("Valid from"))
   else->emptyList()
  }
 }
}

fun initialPassLayout(section:String,category:String):String=when {
 section=="wallets"->"storeCard"
 section=="identities"->"generic"
 category.equals("Boarding Pass",true) || category.equals("Transit",true)->"boardingPass"
 category.lowercase() in setOf("concert","sports","event","event ticket")->"eventTicket"
 category.equals("Coupon",true)->"coupon"
 else->"generic"
}
fun basicFormFields(table:String,section:String,category:String=""):List<String> = when(table) {
 "wallets"->listOf("name","category","number","expiry","network","issuer") + if(category.equals("Credit",true)) listOf("maxlimit","billdate","annualFeeWaiver") else emptyList()
 "identities"->listOf("cardType","name","value","expiry_date")
 else->listOf("organizationName","description","barcodeValue","barcodeFormat","expiry_date") + if(section=="passes" && category.lowercase() !in setOf("coupon","temporary access","other")) listOf("relevantDate") else emptyList()
}
fun paymentExpiry(value:String):String {
 return if(Regex("\\d{2}/\\d{2}").matches(value)) value.replace("/","") else value
}
fun expiryInput(value:String):String=if(Regex("\\d{4}").matches(value)) value.take(2)+"/"+value.takeLast(2) else value
fun validRecordField(key:String,value:String):Boolean {
 if(value.isBlank()) return true
 return when(key) {
  "spends","annualFeeWaiver","maxlimit"->value.toBigDecimalOrNull()!=null
  "billdate"->value.toIntOrNull() in 1..31
  "expiry"->expiryDateValue(value)!=null
  "expiry_date"->expiryDateValue(value)!=null || runCatching {OffsetDateTime.parse(value)}.isSuccess
  "relevantDate"->eventLocal(value)!=null
  else->true
 }
}
fun eventLocal(value:String):LocalDateTime?=runCatching {OffsetDateTime.parse(value).toLocalDateTime()}.getOrNull()
 ?: runCatching {LocalDateTime.parse(value)}.getOrNull()
 ?: customDate(value)?.atStartOfDay()
fun eventWithDate(value:String,date:LocalDate):String {
 val offset=runCatching {OffsetDateTime.parse(value)}.getOrNull()
 if(offset!=null) return OffsetDateTime.of(date,offset.toLocalTime(),offset.offset).toString()
 val time=runCatching {LocalDateTime.parse(value).toLocalTime()}.getOrNull()
 return time?.let {LocalDateTime.of(date,it).toString()} ?: date.toString()
}
fun eventWithTime(value:String,time:LocalTime):String {
 val offset=runCatching {OffsetDateTime.parse(value)}.getOrNull()
 if(offset!=null) return OffsetDateTime.of(offset.toLocalDate(),time,offset.offset).toString()
 return LocalDateTime.of(eventLocal(value)!!.toLocalDate(),time).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun EventDateInput(value:String,change:(String)->Unit,label:String="Event / departure date") {
 var pickingTime by remember {mutableStateOf(false)}
 val local=eventLocal(value)
 ConfiguredFieldInput(CustomField(label,"date","dd/MM/yy"),local?.toLocalDate()?.toString() ?: value,"") {raw->
  change(if(raw.isBlank()) "" else customDate(raw)?.let {eventWithDate(value,it)} ?: raw)
 }
 TextButton(enabled=local!=null,onClick={pickingTime=true}) {
  Icon(Icons.Outlined.Schedule,null);Spacer(Modifier.width(8.dp))
  Text(if(value.contains('T')) "Time: "+local?.toLocalTime().toString() else "Add time (optional)")
 }
 if(pickingTime) CompositionLocalProvider(androidx.compose.runtime.saveable.LocalSaveableStateRegistry provides null) {
  val time=rememberTimePickerState(initialHour=if(value.contains('T')) local!!.hour else 12,initialMinute=if(value.contains('T')) local!!.minute else 0,is24Hour=true)
  AlertDialog(onDismissRequest={pickingTime=false},title={Text(label.replace("date","time"))},text={TimePicker(time)},
   confirmButton={TextButton(onClick={change(eventWithTime(value,LocalTime.of(time.hour,time.minute)));pickingTime=false}) {Text("Set time")}},
   dismissButton={TextButton(onClick={pickingTime=false}) {Text("Cancel")}})
 }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun RecordFieldInput(key:String,label:String,value:String,prefs:VaultPreferences,section:String,change:(String)->Unit) {
 val amount=key in setOf("spends","annualFeeWaiver","maxlimit")
 val date=key in setOf("expiry","expiry_date")
 when {
  key=="relevantDate"->EventDateInput(value,change)
  date->ExpiryFieldInput(label.substringBefore(" ("),value,key=="expiry",change)
  amount->ConfiguredFieldInput(CustomField(label,"currency"),value,prefs.currency,change)
  key in setOf("color","backgroundColor","foregroundColor","labelColor")-> {
   Text(label,style=MaterialTheme.typography.labelLarge)
   FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf("Automatic" to "","Slate" to "#334155","Blue" to "#1D4ED8","Green" to "#166534","Purple" to "#6B21A8","Red" to "#991B1B","Amber" to "#92400E").forEach {(name,hex)->
     FilterChip(value==hex,{change(hex)},label={Text(name)})
    }
   }
   if(value.isNotBlank()) Text("Current color",color=passColor(value) ?: MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.labelSmall)
  }
  else->{
   val options=when(key) {
    "category","cardType"->prefs.categories(section).map {it to it}
    "network"->listOf("Visa","Mastercard","American Express","Discover","JCB","UnionPay","Maestro","Diners Club","RuPay","Other").map {it to it}
    "barcodeFormat"->allBarcodeFormats.map {it to it}
    "type"->listOf("Boarding / transit" to "boardingPass","Event ticket" to "eventTicket","Coupon" to "coupon","Store / membership card" to "storeCard","Generic" to "generic")
    "transitType"->listOf("Air" to "PKTransitTypeAir","Train" to "PKTransitTypeTrain","Bus" to "PKTransitTypeBus","Boat" to "PKTransitTypeBoat","Other" to "PKTransitTypeGeneric")
    "billdate"->(1..31).map {it.toString() to it.toString()}
    else->emptyList()
   }
   var expanded by remember {mutableStateOf(false)}
   if(options.isNotEmpty()) Box {
    OutlinedSelector(label,options.find {it.second==value}?.first ?: value.ifBlank {"Not set"},{expanded=true}) {
     IconButton(onClick={expanded=true}) {Icon(Icons.Outlined.ExpandMore,"Choose "+label)}
    }
    DropdownMenu(expanded,{expanded=false}) {
     if(key !in setOf("category","cardType","type","barcodeFormat")) DropdownMenuItem(text={Text("Not set")},onClick={change("");expanded=false})
     options.forEach {(name,stored)->DropdownMenuItem(text={Text(name)},onClick={change(stored);expanded=false})}
    }
   } else OutlinedTextField(value,change,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),label={Text(label)},modifier=Modifier.fillMaxWidth(),
    keyboardOptions=KeyboardOptions(keyboardType=if(key=="number") KeyboardType.Number else KeyboardType.Text))
  }
 }
}

/** Month-only expiry means the final day of that month; exact dates retain their day. */
fun expiryDateValue(value:String):LocalDate? = runCatching {
 when {
  Regex("\\d{4}").matches(value)->YearMonth.of(2000+value.takeLast(2).toInt(),value.take(2).toInt()).atEndOfMonth()
  Regex("\\d{2}/\\d{2}").matches(value)->YearMonth.of(2000+value.takeLast(2).toInt(),value.take(2).toInt()).atEndOfMonth()
  else->customDate(value) ?: LocalDate.parse(value.take(10))
 }
}.getOrNull()
