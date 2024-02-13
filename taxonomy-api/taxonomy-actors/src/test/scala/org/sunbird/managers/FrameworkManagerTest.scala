package org.sunbird.managers

import org.scalatest.{FlatSpec, Matchers}
import org.scalamock.scalatest.MockFactory
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation, SubGraph}

import java.util
import org.sunbird.mangers.FrameworkManager._

import scala.collection.convert.ImplicitConversions._
import scala.concurrent.ExecutionContext


class FrameworkManagerTest extends FlatSpec with Matchers with MockFactory{

  "FrameworkManager" should "correctly filter framework categories and remove associations" in {
    val framework = new util.HashMap[String, AnyRef]()
    framework.put("name", "framework_test")
    framework.put("code", "framework_test")
    framework.put("description", "desc_test")
    framework.put("channel", "channel_test")
    framework.put("languageCode", new util.ArrayList[String]())
    framework.put("systemDefault", "No")
    framework.put("objectType", "Framework")
    framework.put("status", "Live")
    framework.put("categories", new util.ArrayList[util.Map[String, AnyRef]]())
    framework.put("owner", "in.ekstep")
    framework.put("type", "K-12")

    val category1 = new util.HashMap[String, AnyRef]()
    category1.put("name", "Subject")
    category1.put("code", "subject")
    val category2 = new util.HashMap[String, AnyRef]()
    category2.put("name", "Grade")
    category2.put("code", "grade")
    val categories = new util.ArrayList[util.Map[String, AnyRef]]()
    categories.add(category1)
    categories.add(category2)


    val categoryNames = new util.ArrayList[String]()
    categoryNames.add("subject")

    val term1 = new util.HashMap[String, AnyRef]()
    term1.put("name", "Term1")
    term1.put("code", "term1")
    val associations1 = new util.ArrayList[util.Map[String, AnyRef]]()
    val association1 = new util.HashMap[String, AnyRef]()
    association1.put("category", "Category1")
    associations1.add(association1)
    term1.put("associations", associations1)

    val term2 = new util.HashMap[String, AnyRef]()
    term2.put("name", "Term2")
    term2.put("code", "term2")
    val associations2 = new util.ArrayList[util.Map[String, AnyRef]]()
    val association2 = new util.HashMap[String, AnyRef]()
    association2.put("category", "Category2")
    associations2.add(association2)
    term2.put("associations", associations2)

    category1.put("terms", new util.ArrayList[util.Map[String, AnyRef]]())
    category1.get("terms").asInstanceOf[util.List[util.Map[String, AnyRef]]].add(term1)
    category1.get("terms").asInstanceOf[util.List[util.Map[String, AnyRef]]].add(term2)

    val returnCategories = new util.ArrayList[String]()
    returnCategories.add("Category1")
    returnCategories.add("Category2")

    framework.put("categories", categories)

    val frameworkWithAssociationsRemoved = filterFrameworkCategories(framework, categoryNames)
    val filteredTerms = frameworkWithAssociationsRemoved
      .getOrElse("categories", new util.ArrayList[util.Map[String, AnyRef]]())
      .asInstanceOf[util.List[util.Map[String, AnyRef]]]
      .flatMap(_.getOrDefault("terms", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]])

    assert(filteredTerms.contains(term1))
    assert(filteredTerms.contains(term2))
    assert(!term1.containsKey("associations"))
    assert(!term2.containsKey("associations"))
  }


  }