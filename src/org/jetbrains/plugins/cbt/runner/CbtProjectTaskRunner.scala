package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.executors.{DefaultDebugExecutor, DefaultRunExecutor}
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.remote.{RemoteConfiguration, RemoteConfigurationType}
import com.intellij.execution.runners.{ExecutionEnvironment, ExecutionEnvironmentBuilder}
import com.intellij.execution.{ExecutionManager, Executor, ProgramRunnerUtil, RunManager}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.task._
import com.intellij.task.impl.ExecuteRunConfigurationTaskImpl
import com.intellij.util.Alarm
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings

import scala.collection.JavaConverters._

class CbtProjectTaskRunner extends ProjectTaskRunner {
  val alarm = new Alarm

  override def canRun(projectTask: ProjectTask): Boolean = {
    Option(projectTask).flatMap {
      case task: ModuleBuildTask =>
        Some(task.getModule.getProject)
      case task: ExecuteRunConfigurationTaskImpl =>
        val taskSupported = task.getRunProfile match {
          case _: ApplicationConfiguration => true
          case _ => false
        }
        if (taskSupported)
          Some(task.getSettings.getConfiguration.getProject)
        else
          None
      case _ => None
    }.exists { project =>
      val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
      project.isCbtProject &&
        projectSettings.useCbtForInternalTasks
    }
  }

  override def run(project: Project, context: ProjectTaskContext,
                   callback: ProjectTaskNotification,
                   tasks: util.Collection[_ <: ProjectTask]): Unit = {
    FileDocumentManager.getInstance().saveAllDocuments()
    val moduleBuildTasks = tasks.asScala
      .collect {
        case task: ModuleBuildTask => task
      }
      .toList

    val taskOpt = if (moduleBuildTasks.length == 1)  // If build single module task selected
      moduleBuildTasks.headOption
    else if (moduleBuildTasks.length > 1) // build the whole project selected
      moduleBuildTasks
        .find(_.getModule.getModuleFile.getParent.getCanonicalPath == project.getBaseDir.getCanonicalPath)
    else None
    taskOpt.foreach { task =>
      val workingDir = task.getModule.getModuleFile.getParent.getPath
      buildTask(project, workingDir, callback, task)
    }
  }

  private def buildTask(project: Project, workingDir: String, callback: ProjectTaskNotification, task: ModuleBuildTask) = {
    val taskCallback =
      Option(callback)
        .map { f =>
          () => {
            val request = new Runnable {
              def run(): Unit = {
                f.finished(new ProjectTaskResult(false, 0, 0))
              }
            }
            alarm.addRequest(request, 500)
          }
        }

    val environment = createExecutionEnvironment(project, workingDir, task, taskCallback)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
  }

  private def createExecutionEnvironment(project: Project,  workingDir: String, projectTask: ProjectTask, callback: Option[() => Unit]) = {

    val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
    val configuration = projectTask match {
      case task: ModuleBuildTask =>
        new CbtBuildConfigurationFactory("compile", projectSettings.useDirect, workingDir, CbtConfigurationType.getInstance, callback = callback)
          .createTemplateConfiguration(project)
      case task: ExecuteRunConfigurationTask =>
        val mainClass = task.getRunProfile.asInstanceOf[ApplicationConfiguration].getMainClass.getQualifiedName
        val taskName = s"runMain $mainClass"
        new CbtBuildConfigurationFactory(taskName, projectSettings.useDirect, workingDir, CbtConfigurationType.getInstance, callback = callback)
          .createTemplateConfiguration(project)
    }
    val runner = projectTask match {
      case task: ModuleBuildTask =>
        DefaultJavaProgramRunner.getInstance()
      case task: ExecuteRunConfigurationTask =>
        if (task.getRunnerSettings == null)
          DefaultJavaProgramRunner.getInstance
        else
          new GenericDebuggerRunner
    }
    val runnerSettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance, runner, runnerSettings, project)
    environment
  }

  private def createDebugger(project: Project) = {
    val configType = new RemoteConfigurationType
    val configName = "Debug CBT Task"
    val configFactory = configType.getFactory
    val runManager = RunManager.getInstance(project)

    val runConfig = {
      val rc = runManager.createConfiguration(configName, configFactory)
      runManager.setTemporaryConfiguration(rc)
      rc
    }

    val settings = runConfig.getConfiguration.asInstanceOf[RemoteConfiguration]
    settings.PORT = "5006"
    settings.HOST = "localhost"
    settings.SERVER_MODE = false
    settings.USE_SOCKET_TRANSPORT = true

    val environmentBuilder = ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance, runConfig)
    ProgramRunnerUtil.executeConfiguration(environmentBuilder.build(), false, false)
  }
  override def createExecutionEnvironment(project: Project,
                                          task: ExecuteRunConfigurationTask,
                                          executor: Executor): ExecutionEnvironment = {
    val debug = task.getRunnerSettings != null
    if (debug) {
      createDebugger(project)
    }
    createExecutionEnvironment(project, project.getBaseDir.getPath, task, None)
  }
}



















