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
package com.snowplowanalytics.snowplow.enrich.common
package adapters
package registry

// Joda-Time
import org.joda.time.DateTime

// Scalaz
import scalaz._
import Scalaz._

// json4s
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.scalaz.JsonScalaz._

// Snowplow
import loaders.{
  CollectorApi,
  CollectorSource,
  CollectorContext,
  CollectorPayload
}
import utils.ConversionUtils
import SpecHelpers._

// Specs2
import org.specs2.{Specification, ScalaCheck}
import org.specs2.matcher.DataTables
import org.specs2.scalaz.ValidationMatchers

class MailchimpAdapterSpec extends Specification with DataTables with ValidationMatchers with ScalaCheck { def is =

  "This is a specification to test the MailchimpAdapter functionality"                                                ^
                                                                                                                     p^
  "toKeys should return a valid List of Keys from a string containing braces (or not)"                              ! e1^
  "recurse should return a valid JObject which contains the toKeys list and value supplied"                         ! e2^
  "getJsonObject should return a valid list of JSON Objects which pertains to the map supplied"                     ! e3^
  "mergeJObjects should return a correctly merged JSON which matches the expectation"                               ! e4^
  "getSchema should return the correct schema for a valid event type"                                               ! e5^
  "getSchema should return a Nel Failure error for a bad event type"                                                ! e6^
  "reformateDateTimeForJsonSchema should return a correctly formatted date time string"                             ! e7^
  "reformatBadParamValues should return a parameter Map with correctly formatted values"                            ! e8^
  "toRawEvents must return a Nel Success with a correctly formatted ue_pr json"                                     ! e9^
  "toRawEvents must return a Nel Success with a correctly merged and formatted ue_pr json"                          ! e10^
  "toRawEvents must return a Nel Success for a supported event type"                                                ! e11^
  "toRawEvents must return a Nel Failure error for an unsupported event type"                                       ! e12^
  "toRawEvents must return a Nel Success containing an unsubscribe event and query string parameters"               ! e13^
  "toRawEvents must return a Nel Failure if the body content is empty"                                              ! e14^
  "toRawEvents must return a Nel Failure if no type parameter is passed in the body"                                ! e15^
                                                                                                                     end
  implicit val resolver = SpecHelpers.IgluResolver

  object Shared {
    val api = CollectorApi("com.mailchimp", "v1")
    val cljSource = CollectorSource("clj-tomcat", "UTF-8", None)
    val context = CollectorContext(DateTime.parse("2013-08-29T00:18:48.000+00:00"), "37.157.33.123".some, None, None, Nil, None)
  }

  val ContentType = "application/x-www-form-urlencoded; charset=utf-8"

  def e1 = {
    val toKeysTest = MailchimpAdapter.toKeys("data[merges][LNAME]")
    val expected = NonEmptyList("data","merges","LNAME")
    toKeysTest mustEqual expected
  }

  def e2 = {
    val keysArray = NonEmptyList("data","merges","LNAME")
    val value = "Beemster"
    val expected = JObject(List(("data",JObject(List(("merges",JObject(List(("LNAME",JString("Beemster"))))))))))
    val testRecursive = MailchimpAdapter.recurse(keysArray, value)
    testRecursive mustEqual expected
  }

  def e3 = {
    val m = Map("data[merges][LNAME]" -> "Beemster")
    val expected = List(JObject(List(("data",JObject(List(("merges",JObject(List(("LNAME",JString("Beemster")))))))))))
    val testMap = MailchimpAdapter.getJsonObject(m)
    testMap mustEqual expected
  }

  def e4 = {
    val m = Map("data[merges][LNAME]" -> "Beemster", "data[merges][FNAME]" -> "Joshua")
    val jsonObject = MailchimpAdapter.getJsonObject(m)
    val actual = MailchimpAdapter.mergeJObjects(jsonObject)
    val expected = JObject(List(("data",JObject(List(("merges",JObject(List(("LNAME",JString("Beemster")), ("FNAME",JString("Joshua"))))))))))
    actual mustEqual expected
  }

  def e5 = 
    "SPEC NAME"               || "SCHEMA TYPE"  | "EXPECTED OUTPUT"                                                |
    "Valid, type subscribe"   !! "subscribe"    ! "iglu:com.mailchimp/subscribe/jsonschema/1-0-0"                  |
    "Valid, type unsubscribe" !! "unsubscribe"  ! "iglu:com.mailchimp/unsubscribe/jsonschema/1-0-0"                |
    "Valid, type profile"     !! "profile"      ! "iglu:com.mailchimp/profile_update/jsonschema/1-0-0"             |
    "Valid, type email"       !! "upemail"      ! "iglu:com.mailchimp/email_address_change/jsonschema/1-0-0"       |
    "Valid, type cleaned"     !! "cleaned"      ! "iglu:com.mailchimp/cleaned_email/jsonschema/1-0-0"              |
    "Valid, type campaign"    !! "campaign"     ! "iglu:com.mailchimp/campaign_sending_status/jsonschema/1-0-0"    |> {
      (_, schema, expected) => MailchimpAdapter.lookupSchema(schema) must beSuccessful(expected)
  }

  def e6 = 
    "SPEC NAME"               || "SCHEMA TYPE"  | "EXPECTED OUTPUT"                                                |
    "Invalid, bad type"       !! "bad"          ! "MailChimp type parameter [bad] not recognized"                  |
    "Invalid, no type"        !! ""             ! "MailChimp type parameter is empty: cannot determine event type" |> {
      (_, schema, expected) => MailchimpAdapter.lookupSchema(schema) must beFailing(expected)
  }

  def e7 = {
    val dt = "2014-10-22 13:50:00"
    val dtOut = MailchimpAdapter.reformateDateTimeForJsonSchema(dt)
    val expected = "2014-10-22T13:50:00Z"
    dtOut mustEqual expected
  }

  def e8 =
    "SPEC NAME"             || "PARAMS"                                                        | "EXPECTED OUTPUT"                                                |
    "Return Updated Params" !! Map("type" -> "subscribe", "fired_at" -> "2014-10-22 13:50:00") ! Map("type" -> "subscribe", "fired_at" -> "2014-10-22T13:50:00Z") |
    "Return Same Params"    !! Map("type" -> "subscribe", "id" -> "some_id")                   ! Map("type" -> "subscribe", "id" -> "some_id")                    |> {
      (_, params, expected) => 
      val actual = MailchimpAdapter.reformatBadParamValues(params)
      actual mustEqual expected
  }

  def e9 = {
    val body = "type=subscribe&data%5Bmerges%5D%5BLNAME%5D=Beemster"
    val payload = CollectorPayload(Shared.api, Nil, ContentType.some, body.some, Shared.cljSource, Shared.context)
    val expectedJson = 
      """|{
            |"schema":"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0",
            |"data":{
              |"schema":"iglu:com.mailchimp/subscribe/jsonschema/1-0-0",
              |"data":{
                |"type":"subscribe",
                |"data":{
                  |"merges":{
                    |"LNAME":"Beemster"
                  |}
                |}
              |}
            |}
          |}""".stripMargin.replaceAll("[\n\r]","")

    val actual = MailchimpAdapter.toRawEvents(payload)
    actual must beSuccessful(NonEmptyList(RawEvent(Shared.api, Map("tv" -> "com.mailchimp-v1", "e" -> "ue", "p" -> "srv", "ue_pr" -> expectedJson), ContentType.some, Shared.cljSource, Shared.context)))
  }

  def e10 = {
    val body = "type=subscribe&data%5Bmerges%5D%5BFNAME%5D=Agent&data%5Bmerges%5D%5BLNAME%5D=Smith"
    val payload = CollectorPayload(Shared.api, Nil, ContentType.some, body.some, Shared.cljSource, Shared.context)
    val expectedJson = 
      """|{
            |"schema":"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0",
            |"data":{
              |"schema":"iglu:com.mailchimp/subscribe/jsonschema/1-0-0",
              |"data":{
                |"type":"subscribe",
                |"data":{
                  |"merges":{
                    |"FNAME":"Agent",
                    |"LNAME":"Smith"
                  |}
                |}
              |}
            |}
          |}""".stripMargin.replaceAll("[\n\r]","")

    val actual = MailchimpAdapter.toRawEvents(payload)
    actual must beSuccessful(NonEmptyList(RawEvent(Shared.api, Map("tv" -> "com.mailchimp-v1", "e" -> "ue", "p" -> "srv", "ue_pr" -> expectedJson), ContentType.some, Shared.cljSource, Shared.context)))
  }

  def e11 = 
    "SPEC NAME"               || "SCHEMA TYPE"  | "EXPECTED SCHEMA"                                               |
    "Valid, type subscribe"   !! "subscribe"    ! "iglu:com.mailchimp/subscribe/jsonschema/1-0-0"                 |
    "Valid, type unsubscribe" !! "unsubscribe"  ! "iglu:com.mailchimp/unsubscribe/jsonschema/1-0-0"               |
    "Valid, type profile"     !! "profile"      ! "iglu:com.mailchimp/profile_update/jsonschema/1-0-0"            |
    "Valid, type email"       !! "upemail"      ! "iglu:com.mailchimp/email_address_change/jsonschema/1-0-0"      |
    "Valid, type cleaned"     !! "cleaned"      ! "iglu:com.mailchimp/cleaned_email/jsonschema/1-0-0"             |
    "Valid, type campaign"    !! "campaign"     ! "iglu:com.mailchimp/campaign_sending_status/jsonschema/1-0-0"   |> {
      (_, schema, expected) => 
        val body = "type="+schema
        val payload = CollectorPayload(Shared.api, Nil, ContentType.some, body.some, Shared.cljSource, Shared.context)
        val expectedJson = "{\"schema\":\"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0\",\"data\":{\"schema\":\""+expected+"\",\"data\":{\"type\":\""+schema+"\"}}}"
        val actual = MailchimpAdapter.toRawEvents(payload)
        actual must beSuccessful(NonEmptyList(RawEvent(Shared.api, Map("tv" -> "com.mailchimp-v1", "e" -> "ue", "p" -> "srv", "ue_pr" -> expectedJson), ContentType.some, Shared.cljSource, Shared.context)))
  }

  def e12 = 
    "SPEC NAME"               || "SCHEMA TYPE"  | "EXPECTED OUTPUT"                                                   |
    "Invalid, bad type"       !! "bad"          ! "MailChimp type parameter [bad] not recognized"                     |
    "Invalid, no type"        !! ""             ! "MailChimp type parameter is empty: cannot determine event type"    |> {
      (_, schema, expected) =>
        val body = "type="+schema
        val payload = CollectorPayload(Shared.api, Nil, ContentType.some, body.some, Shared.cljSource, Shared.context)
        val actual = MailchimpAdapter.toRawEvents(payload)
        actual must beFailing(NonEmptyList(expected))
  }

  def e13 = {
    val body = "type=unsubscribe&fired_at=2014-10-22+13%3A10%3A40&data%5Baction%5D=unsub&data%5Breason%5D=manual&data%5Bid%5D=94826aa750&data%5Bemail%5D=josh%40snowplowanalytics.com&data%5Bemail_type%5D=html&data%5Bip_opt%5D=82.225.169.220&data%5Bweb_id%5D=203740265&data%5Bmerges%5D%5BEMAIL%5D=josh%40snowplowanalytics.com&data%5Bmerges%5D%5BFNAME%5D=Joshua&data%5Bmerges%5D%5BLNAME%5D=Beemster&data%5Blist_id%5D=f1243a3b12"
    val qs = toNameValuePairs("nuid" -> "123")
    val payload = CollectorPayload(Shared.api, qs, ContentType.some, body.some, Shared.cljSource, Shared.context)
    val expectedJson = 
      """|{
            |"schema":"iglu:com.snowplowanalytics.snowplow/unstruct_event/jsonschema/1-0-0",
            |"data":{
              |"schema":"iglu:com.mailchimp/unsubscribe/jsonschema/1-0-0",
              |"data":{
                |"data":{
                  |"ip_opt":"82.225.169.220",
                  |"merges":{
                    |"LNAME":"Beemster",
                    |"FNAME":"Joshua",
                    |"EMAIL":"josh@snowplowanalytics.com"
                  |},
                  |"email":"josh@snowplowanalytics.com",
                  |"list_id":"f1243a3b12",
                  |"email_type":"html",
                  |"reason":"manual",
                  |"id":"94826aa750",
                  |"action":"unsub",
                  |"web_id":"203740265"
                |},
                |"fired_at":"2014-10-22T13:10:40.000Z",
                |"type":"unsubscribe"
              |}
            |}
          |}""".stripMargin.replaceAll("[\n\r]","")

    val actual = MailchimpAdapter.toRawEvents(payload)
    actual must beSuccessful(NonEmptyList(RawEvent(Shared.api, Map("tv" -> "com.mailchimp-v1", "e" -> "ue", "p" -> "srv", "ue_pr" -> expectedJson, "nuid" -> "123"), ContentType.some, Shared.cljSource, Shared.context)))
  }

  def e14 = {
    val body = ""
    val payload = CollectorPayload(Shared.api, Nil, ContentType.some, body.some, Shared.cljSource, Shared.context)
    val actual = MailchimpAdapter.toRawEvents(payload)
    actual must beFailing(NonEmptyList("No MailChimp type parameter provided: cannot determine event type"))
  }

  def e15 = {
    val body = "fired_at=2014-10-22+13%3A10%3A40"
    val payload = CollectorPayload(Shared.api, Nil, ContentType.some, body.some, Shared.cljSource, Shared.context)
    val actual = MailchimpAdapter.toRawEvents(payload)
    actual must beFailing(NonEmptyList("No MailChimp type parameter provided: cannot determine event type"))
  }
}
