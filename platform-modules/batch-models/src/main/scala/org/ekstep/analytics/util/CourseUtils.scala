package org.ekstep.analytics.util

import org.apache.spark.SparkContext
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.{DataFrame, SQLContext, SparkSession}
import org.ekstep.analytics.framework.FrameworkContext
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.framework.util.{JSONUtils, RestUtil}
import org.ekstep.analytics.model.{OutputConfig, ReportConfig}
import org.sunbird.cloud.storage.BaseStorageService

//Getting live courses from compositesearch
case class CourseDetails(result: Result)
case class Result(content: List[CourseInfo])
case class CourseInfo(channel: String, identifier: String, name: String)

trait CourseReport {
  def getCourse(config: Map[String, AnyRef])(sc: SparkContext): DataFrame

  def loadData(spark: SparkSession, settings: Map[String, String]): DataFrame

  def getCourseBatchDetails(spark: SparkSession, loadData: (SparkSession, Map[String, String]) => DataFrame): DataFrame
  def getTenantInfo(spark: SparkSession, loadData: (SparkSession, Map[String, String]) => DataFrame): DataFrame
  def writeToCSVAndRename(data: DataFrame, config: Map[String, AnyRef])(implicit sc: SparkContext): String
}

object CourseUtils {

  def getCourse(config: Map[String, AnyRef])(implicit sc: SparkContext, fc: FrameworkContext, sqlContext: SQLContext): DataFrame = {
    import sqlContext.implicits._
    val apiURL = Constants.COMPOSITE_SEARCH_URL
    val request = JSONUtils.serialize(config.get("esConfig").get)
    val response = RestUtil.post[CourseDetails](apiURL, request).result.content
    val resRDD = sc.parallelize(response)
    resRDD.toDF("channel", "identifier", "courseName")

  }

  def loadData(settings: Map[String, String])(implicit sc: SparkContext, sqlContext: SQLContext): DataFrame = {

    sqlContext.sparkSession
      .read
      .format("org.apache.spark.sql.cassandra")
      .option("spark.cassandra.connection.host", AppConf.getConfig("spark.cassandra.connection.host"))
      .options(settings)
      .load()
  }

  def getCourseBatchDetails()(implicit sc: SparkContext, sqlContext: SQLContext): DataFrame = {
    val sunbirdCoursesKeyspace = Constants.SUNBIRD_COURSES_KEY_SPACE
    loadData(Map("table" -> "course_batch", "keyspace" -> sunbirdCoursesKeyspace))
      .select(
        col("courseid").as("courseId"),
        col("batchid").as("batchId"),
        col("name").as("batchName"),
        col("status").as("status")
      )
  }

  def getTenantInfo()(implicit sc: SparkContext, sqlContext: SQLContext): DataFrame = {
    val sunbirdKeyspace = AppConf.getConfig("course.metrics.cassandra.sunbirdKeyspace")
    loadData(Map("table" -> "organisation", "keyspace" -> sunbirdKeyspace)).select("slug","id")
  }

  def postDataToBlob(data: DataFrame, outputConfig: OutputConfig, config: Map[String, AnyRef], storageService: BaseStorageService)(implicit sc: SparkContext, fc: FrameworkContext) = {
    val configMap = config("reportConfig").asInstanceOf[Map[String, AnyRef]]
    val reportConfig = JSONUtils.deserialize[ReportConfig](JSONUtils.serialize(configMap))

    val labelsLookup = reportConfig.labels ++ Map("date" -> "Date")
    val key = config.getOrElse("key", null).asInstanceOf[String]

    val metricFields = outputConfig.metrics
    val fieldsList = data.columns
    val dimsLabels = labelsLookup.filter(x => outputConfig.dims.contains(x._1)).values.toList
    val filteredDf = data.select(fieldsList.head, fieldsList.tail: _*)
    val renamedDf = filteredDf.select(filteredDf.columns.map(c => filteredDf.col(c).as(labelsLookup.getOrElse(c, c))): _*)
    val reportFinalId = if (outputConfig.label.nonEmpty && outputConfig.label.get.nonEmpty) reportConfig.id + "/" + outputConfig.label.get else reportConfig.id

    val finalDf = renamedDf.na.replace("status",Map("0"->BatchStatus(0).toString, "1"->BatchStatus(1).toString, "2"->BatchStatus(2).toString))
    finalDf.show()
    val dirPath = writeToCSVAndRename(finalDf, config ++ Map("dims" -> dimsLabels, "reportId" -> reportFinalId, "fileParameters" -> outputConfig.fileParameters))
    val bucket = config.getOrElse("bucket", "telemetry-data-store").asInstanceOf[String]
    storageService.upload(bucket, (dirPath + reportFinalId + "/"), (key + reportFinalId + "/"), Option(true), Option(1), Option(3), None)
  }

  def writeToCSVAndRename(data: DataFrame, config: Map[String, AnyRef])(implicit sc: SparkContext, fc: FrameworkContext): String = {
    val filePath = config.getOrElse("filePath", AppConf.getConfig("spark_output_temp_dir")).asInstanceOf[String]
    val key = config.getOrElse("key", null).asInstanceOf[String]
    val reportId = config.getOrElse("reportId", "").asInstanceOf[String]
    val fileParameters = config.getOrElse("fileParameters", List("")).asInstanceOf[List[String]]
    var dims = config.getOrElse("folderPrefix", List()).asInstanceOf[List[String]]

    dims = if (fileParameters.nonEmpty && fileParameters.contains("date")) dims else dims
    val finalPath = filePath + key.split("/").last

    if(dims.nonEmpty) {
      data.coalesce(1).write.format("com.databricks.spark.csv").option("header", "true").partitionBy(dims: _*).mode("overwrite").save(finalPath)
    } else
      data.coalesce(1).write.format("com.databricks.spark.csv").option("header", "true").mode("overwrite").save(finalPath)

    val renameDir = finalPath + "/renamed/"
    FileUtil.renameHadoopFiles(finalPath, renameDir, reportId, dims)
  }
}
