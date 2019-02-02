/**
 * Copyright 2015 SIA "Edible Day"
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.edibleday

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.teavm.tooling.TeaVMTargetType
import org.teavm.tooling.TeaVMTool
import org.teavm.tooling.sources.DirectorySourceFileProvider
import org.teavm.tooling.sources.JarSourceFileProvider
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URLClassLoader

open class TeaVMTask : DefaultTask() {

	var installDirectory: String = File(project.buildDir, "teavm").absolutePath
	var targetFileName: String = "app.js"
	var copySources: Boolean = false
	var generateSourceMap: Boolean = false
	var minified: Boolean = true
	var targetType: String = "javascript"

	val gradleLog = Logging.getLogger(TeaVMTask::class.java)
	val log by lazy { TeaVMLoggerGlue(project.logger) }

	@TaskAction
	fun compTeaVM() {
		val tool = TeaVMTool()
		val project = project

		tool.targetDirectory = File(installDirectory)
		tool.targetFileName = targetFileName

		if (project.hasProperty("mainClassName") && project.property("mainClassName") != null) {
			tool.mainClass = "${project.property("mainClassName")}"
		} else throw TeaVMException("mainClassName not found!")


		fun addSrc(f: File) {
			if (f.isFile) {
				if (f.absolutePath.endsWith(".jar")) {
					tool.addSourceFileProvider(JarSourceFileProvider(f))
				} else {
					tool.addSourceFileProvider(DirectorySourceFileProvider(f))
				}
			} else {
				tool.addSourceFileProvider(DirectorySourceFileProvider(f))
			}

		}


		val convention = project.convention.getPlugin(JavaPluginConvention::class.java)

		convention
				.sourceSets
				.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
				.allSource
				.srcDirs.forEach(::addSrc)

		project
				.configurations
				.getByName("teavmsources")
				.files
				.forEach(::addSrc)

		val cacheDirectory = File(project.buildDir, "teavm-cache")
		cacheDirectory.mkdirs()
		tool.cacheDirectory = cacheDirectory
		tool.isMinifying = minified
		tool.log = log
		tool.isSourceFilesCopied = copySources
		tool.isSourceMapsFileGenerated = generateSourceMap
		tool.targetType = TeaVMTargetType.valueOf(targetType.toUpperCase());

		val classLoader = prepareClassLoader()
		try {
			tool.classLoader = classLoader
			tool.generate()
		} finally {
			try {
				classLoader.close()
			} catch (ignored: IOException) {
			}
		}

	}


	private fun prepareClassLoader(): URLClassLoader {
		try {
			val urls = project.configurations.getByName("runtime").run {
				val dependencies = files.map { it.toURI().toURL() }
				val artifacts = allArtifacts.files.map { it.toURI().toURL() }
				dependencies + artifacts
			}
			gradleLog.info("Using classpath URLs: {}", urls)

			return URLClassLoader(urls.toTypedArray(), javaClass.classLoader)
		} catch (e: MalformedURLException) {
			throw GradleException("Error gathering classpath information", e)
		}
	}


}
