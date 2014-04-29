/*
 * Copyright (c) 2012-2014 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.enrich
package hadoop
package good

// Scala
import scala.collection.mutable.Buffer

// Specs2
import org.specs2.mutable.Specification

// Scalding
import com.twitter.scalding._

// Cascading
import cascading.tuple.TupleEntry

// This project
import JobSpecHelpers._

/**
 * Holds the input and expected data
 * for the test.
 */
object Apr2014CfLineSpec {

  // August 2013: Amazon broke the CloudFront access log file format. They stopped double-encoding the querystring
  val lines = Lines(
    "2014-04-29	09:00:54	CDG51	830	83.167.37.146	GET	d3v6ndkyapxc2w.cloudfront.net	/i	200	http://snowplowanalytics.com/blog/2013/11/20/loading-json-data-into-redshift/	Mozilla/5.0%2520(Macintosh;%2520Intel%2520Mac%2520OS%2520X%252010_9_2)%2520AppleWebKit/537.36%2520(KHTML,%2520like%2520Gecko)%2520Chrome/34.0.1847.131%2520Safari/537.36	e=pp&page=Loading%2520JSON%2520data%2520into%2520Redshift%2520-%2520the%2520challenges%2520of%2520quering%2520JSON%2520data%252C%2520and%2520how%2520Snowplow%2520can%2520be%2520used%2520to%2520meet%2520those%2520challenges&pp_mix=0&pp_max=1&pp_miy=64&pp_may=935&cx=eyJwYWdlIjp7InVybCI6ImJsb2cifX0&dtm=1398762054889&tid=612876&vp=1279x610&ds=1279x5614&vid=2&duid=44082d3af0e30126&p=web&tv=2.0.0&fp=2071613637&aid=snowplowweb&lang=fr&cs=UTF-8&tz=Europe%252FBerlin&tna=cloudfront&evn=com.snowplowanalytics&refr=http%253A%252F%252Fsnowplowanalytics.com%252Fservices%252Fpipelines.html&f_pdf=1&f_qt=1&f_realp=0&f_wma=0&f_dir=0&f_fla=1&f_java=1&f_gears=0&f_ag=0&res=1280x800&cd=24&cookie=1&url=http%253A%252F%252Fsnowplowanalytics.com%252Fblog%252F2013%252F11%252F20%252Floading-json-data-into-redshift%252F%2523weaknesses	-	Hit	cN-iKWE_3tTwxIKGkSnUOjGpmNjsDUyk4ctemoxU_zIG7Md_fH87sg==	d3v6ndkyapxc2w.cloudfront.net	http	1163	0.001"
    )

  val expected = List(
    "snowplowweb",
    "web",
    "2013-08-29 00:18:48.000",
    "2013-08-29 00:19:17.970",
    "page_view",
    null, // No event vendor set
    null, // We can't predict the event_id
    "567074",
    "main", // Tracker namespace
    "js-0.12.0",
    "cloudfront",
    EtlVersion,
    null, // No user_id set
    "255.255.255.255",
    "308909339",
    "7969620089de36eb",
    "1",
    null, // No network_userid set
    null, // No geo-location for this IP address
    null,
    null,
    null,
    null,
    null,
    "http://snowplowanalytics.com/analytics/index.html",
    "Introduction - Snowplow Analytics%",
    "http://www.metacrawler.com/search/web?fcoid=417&fcop=topnav&fpid=27&q=snowplow+analytics&ql=",
    "http",
    "snowplowanalytics.com",
    "80",
    "/analytics/index.html",
    null,
    null,
    "http",
    "www.metacrawler.com",
    "80",
    "/search/web",
    "fcoid=417&fcop=topnav&fpid=27&q=snowplow+analytics&ql=",
    null,
    "search", // Search referer
    "InfoSpace",
    "snowplow analytics",
    null, // Marketing campaign fields empty
    null, //
    null, //
    null, //
    null, //
    null, // No custom contexts
    null, // Structured event fields empty
    null, //
    null, //
    null, //
    null, //
    null, // Unstructured event fields empty
    null, //
    null, // Transaction fields empty 
    null, //
    null, //
    null, //
    null, //
    null, //
    null, //
    null, //
    null, // Transaction item fields empty
    null, //
    null, //
    null, //
    null, //
    null, //
    null, // Page ping fields empty
    null, //
    null, //
    null, //
    "Mozilla/5.0 (Windows NT 5.1; rv:23.0) Gecko/20100101 Firefox/23.0",
    "Firefox 23",
    "Firefox",
    "23.0",
    "Browser",
    "GECKO",
    "en-US",
    "1",
    "1",
    "1",
    "0",
    "1",
    "0",
    "1",
    "0",
    "0",
    "1",
    "24",
    "1024",
    "635",
    "Windows",
    "Windows",
    "Microsoft Corporation",
    "America/Los_Angeles",
    "Computer",
    "0",
    "1024",
    "768",
    "UTF-8",
    "1024",
    "635"
    )
}

/**
 * Integration test for the EtlJob:
 *
 * Check that all tuples in a page view in the
 * CloudFront format changed in April 2014
 * are successfully extracted.
 *
 * For details:
 * https://forums.aws.amazon.com/thread.jspa?threadID=134017&tstart=0#
 */
class Apr2014CfLineSpec extends Specification with TupleConversions {

  "A job which processes a CloudFront file containing 1 valid page view" should {
    EtlJobSpec("cloudfront", "0").
      source(MultipleTextLineFiles("inputFolder"), Apr2014CfLineSpec.lines).
      sink[TupleEntry](Tsv("outputFolder")){ buf : Buffer[TupleEntry] =>
        "correctly output 1 page ping" in {
          buf.size must_== 1
          val actual = buf.head
          for (idx <- Apr2014CfLineSpec.expected.indices) {
            actual.getString(idx) must beFieldEqualTo(Apr2014CfLineSpec.expected(idx), withIndex = idx)
          }
        }
      }.
      sink[TupleEntry](Tsv("exceptionsFolder")){ trap =>
        "not trap any exceptions" in {
          trap must beEmpty
        }
      }.
      sink[String](JsonLine("badFolder")){ error =>
        "not write any bad rows" in {
          error must beEmpty
        }
      }.
      run.
      finish
  }
}