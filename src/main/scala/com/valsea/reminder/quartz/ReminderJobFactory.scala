package com.valsea.reminder.quartz

import org.quartz.simpl.PropertySettingJobFactory
import org.quartz.spi.TriggerFiredBundle
import org.quartz.{Job, Scheduler}

// This factory is needed to have the opportunity to inject dependencies. Job data map values must be serializable
// and these dependencies (the http service, cluster sharding references, etc) are not. They must be injected.
// PropertySettingJobFactory is used to auto fill job values from the jobDataMap in a bean fashion way
class ReminderJobFactory(quartzRuntimeDependencies: QuartzRuntimeDependencies)
    extends PropertySettingJobFactory {
  override def newJob(bundle: TriggerFiredBundle, Scheduler: Scheduler): Job = {
    val job = super.newJob(bundle, Scheduler).asInstanceOf[ReminderJob]
    job.setDependencies(quartzRuntimeDependencies)
    job
  }
}
