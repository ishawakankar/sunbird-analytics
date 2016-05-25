package org.ekstep.analytics.model

import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.OutputDispatcher
import org.ekstep.analytics.framework.Dispatcher

class TestContentUsageSummary extends SparkSpec(null) {

    it should "generate content summary events" in {
        val rdd = loadFile[MeasuredEvent]("src/test/resources/content-usage-summary/multiple-dates/*");
        val rdd2 = ContentUsageSummary.execute(rdd, None);
        val events = rdd2.collect
        events.length should be(29)
        val summ = JSONUtils.deserialize[MeasuredEvent](events(0))
        summ.content_id.get should be("org.ekstep.story.ka.elephant")
        summ.dimensions.group_user.get should be(false)

        val eksMap = summ.edata.eks.asInstanceOf[Map[String, AnyRef]]
        eksMap.get("total_ts").get should be(196.8)
        eksMap.get("total_sessions").get should be(8)
        eksMap.get("avg_ts_session").get should be(24.6)
        eksMap.get("total_interactions").get should be(89)
        eksMap.get("avg_interactions_min").get should be(27.13)
        eksMap.get("content_type").get should be("Story")
        eksMap.get("mime_type").get should be("application/vnd.ekstep.ecml-archive")
    }

    it should "generate content summary events where timeSpent=0 and noOfInteractEvents=0 in the input data" in {
        val rdd = loadFile[MeasuredEvent]("src/test/resources/content-usage-summary/test_data1.log");
        val rdd2 = ContentUsageSummary.execute(rdd, None);
        val events = rdd2.collect
        events.length should be(1)

        val summ = JSONUtils.deserialize[MeasuredEvent](events.last)
        summ.content_id.get should be("numeracy_369")
        summ.dimensions.group_user.get should be(false)

        val eksMap = summ.edata.eks.asInstanceOf[Map[String, AnyRef]]
        eksMap.get("total_ts").get should be(0)
        eksMap.get("total_sessions").get should be(109)
        eksMap.get("avg_ts_session").get should be(0)
        eksMap.get("total_interactions").get should be(0)
        eksMap.get("avg_interactions_min").get should be(0)
        eksMap.get("content_type").get should be("Game")
        eksMap.get("mime_type").get should be("application/vnd.ekstep.ecml-archive")
    }

    it should "generate content summary from input events with zero timeSpent and non-zero noOfInteractEvents for multiple content_id" in {
        val rdd = loadFile[MeasuredEvent]("src/test/resources/content-usage-summary/test_data2.log");
        val rdd2 = ContentUsageSummary.execute(rdd, None);
        val events = rdd2.collect
        events.length should be(4)

        for (event <- events) {
            val summ = JSONUtils.deserialize[MeasuredEvent](event)

            val eksMap = summ.edata.eks.asInstanceOf[Map[String, AnyRef]]
            eksMap.get("total_ts").get should not be(0)
            eksMap.get("avg_ts_session").get should not be(0)
            eksMap.get("total_interactions").get should be (0)
            eksMap.get("avg_interactions_min").get should be (0)
        }
    }
    
    it should "generate content summary from input events with non-zero timeSpent and non-zero noOfInteractEvents" in {
        val rdd = loadFile[MeasuredEvent]("src/test/resources/content-usage-summary/test_data3.log");
        val rdd2 = ContentUsageSummary.execute(rdd, None);
        
        val events = rdd2.collect
        events.length should be(3)
        
        for (event <- events) {
            val summ = JSONUtils.deserialize[MeasuredEvent](event)

            val eksMap = summ.edata.eks.asInstanceOf[Map[String, AnyRef]]
            eksMap.get("total_ts").get should not be(0)
            eksMap.get("avg_ts_session").get should not be(0)
            eksMap.get("total_interactions").get should not be (0)
            eksMap.get("avg_interactions_min").get should not be (0)
        }
    }
    
    it should "generate content summary from input events with non-zero timeSpent and non-zero noOfInteractEvents and groupUser = true" in {
        val rdd = loadFile[MeasuredEvent]("src/test/resources/content-usage-summary/test_data4.log");
        val rdd2 = ContentUsageSummary.execute(rdd, None);
        
        val events = rdd2.collect
        events.length should be(5)
        
        for (event <- events) {
            val summ = JSONUtils.deserialize[MeasuredEvent](event)

            val eksMap = summ.edata.eks.asInstanceOf[Map[String, AnyRef]]
            eksMap.get("total_ts").get should not be(0)
            eksMap.get("avg_ts_session").get should not be(0)
            eksMap.get("total_interactions").get should not be (0)
            eksMap.get("avg_interactions_min").get should not be (0)
        }
    }
}