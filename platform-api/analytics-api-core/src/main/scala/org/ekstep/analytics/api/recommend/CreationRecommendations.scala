package org.ekstep.analytics.api.recommend

import org.ekstep.analytics.api.IRecommendations
import org.ekstep.analytics.api.RequestBody
import com.typesafe.config.Config
import org.apache.spark.SparkContext
import com.datastax.spark.connector._
import org.ekstep.analytics.api.util.ContentCacheUtil
import org.ekstep.analytics.api.util.JSONUtils
import org.ekstep.analytics.api.util.CommonUtil
import org.ekstep.analytics.api.APIIds
import org.ekstep.analytics.api.Constants
import org.ekstep.analytics.api.ResponseCode
import org.apache.commons.lang3.StringUtils
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.framework.dispatcher.GraphQueryDispatcher
import org.ekstep.analytics.framework.DataNode
import scala.collection.JavaConverters._
import org.apache.commons.lang3.StringUtils
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.JobContext
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.RelationshipDirection
import org.ekstep.analytics.framework.dispatcher.GraphQueryDispatcher
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.framework.Relation
import org.ekstep.analytics.framework.GraphQueryParams._
import org.ekstep.analytics.framework.util.GraphDBUtil
import scala.collection.mutable.ListBuffer
import scala.util.Random
import org.ekstep.analytics.api.RequestRecommendations
import org.ekstep.analytics.api.CreationRequestList
import org.ekstep.analytics.api.CreationRequest

object CreationRecommendations extends IRecommendations {

    def isValidRequest(requestBody: RequestBody): Validation = {
        val context = requestBody.request.context.getOrElse(Map());
        val authorid = context.getOrElse("uid", "").asInstanceOf[String];
        if (StringUtils.isEmpty(authorid))
            Validation(false, Option("uid should be present"));
        else
            Validation(true);
    }

    def fetch(requestBody: RequestBody)(implicit sc: SparkContext, config: Config): String = {
        val validation = isValidRequest(requestBody)
        if (validation.value) {
            val context = requestBody.request.context.getOrElse(Map());
            val authorId = context.getOrElse("uid", "").asInstanceOf[String];
           	val requestsFromCassandra = sc.cassandraTable[CreationRequestList](Constants.PLATFORML_DB, Constants.REQUEST_RECOS_TABLE).select("requests").where("uid = ?", authorId).collect().toList.flatMap { x => x.requests };           
            val getrequests = getRequestList(requestsFromCassandra)
            val result = applyLimit(getrequests, getrequests.size, getLimit(requestBody));
            JSONUtils.serialize(CommonUtil.OK(APIIds.CREATION_RECOMMENDATIONS, Map[String, AnyRef]("requests" -> result)));
        } else {
            CommonUtil.errorResponseSerialized(APIIds.CREATION_RECOMMENDATIONS, "context required data is missing.", ResponseCode.CLIENT_ERROR.toString());
        }
    }

    def applyLimit(contents: List[Map[String, Any]], total: Int, limit: Int)(implicit config: Config): List[Map[String, Any]] = {
        contents.take(limit);
    }

    private def getRequestList(list: List[CreationRequest]): List[Map[String, AnyRef]] = {
        val requests = for (creation <- list) yield {
            Map("type" -> creation.`type`,
                "language" -> creation.language,
                "concepts" -> creation.concepts,
                "contentType" -> creation.content_type,
                "gradeLevel" -> creation.grade_level)
        }
        requests.toList
    }
    
}