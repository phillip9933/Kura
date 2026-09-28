package app.kura.nativeapp

/** Removes only the five deliberately malformed gallery fixtures created by LaunchTest. */
fun removeUiGalleryFixtures() {
    require(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
    val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    val controller=arrayOfNulls<VaultController>(1)
    instrumentation.runOnMainSync {
        val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
        controller[0]=androidx.lifecycle.ViewModelProvider(activity)[VaultController::class.java]
    }
    kotlinx.coroutines.runBlocking {
        controller[0]!!.work { opened ->
            val names=listOf("boardingPass","coupon","storeCard","eventTicket","generic").map {"UI example "+it}
            controller[0]!!.store.rows(opened,"passes")
                .filter {it.optString("organizationName") in names && it.optString("barcodeValue")=="TEST"}
                .forEach {controller[0]!!.store.deletePass(opened,it.getLong("id"))}
        }
        controller[0]!!.refresh()
    }
}
