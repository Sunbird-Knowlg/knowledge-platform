package org.sunbird.utils

import java.util
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.dac.enums.RelationTypes
import org.sunbird.utils.taxonomy.TaxonomyUtil

/**
 * Unit tests for TaxonomyUtil.
 *
 * Covers the two extracted shared utilities:
 *   - generateIdentifier(scopeId, code): produces a slugified composite ID
 *   - getNextSequenceIndex(node): returns max existing sequence index + 1
 */
class TaxonomyUtilTest extends FlatSpec with Matchers {

  // ─── generateIdentifier ────────────────────────────────────────────────────

  "TaxonomyUtil.generateIdentifier" should
    "return a slugified identifier when both scopeId and code are provided" in {
    val result = TaxonomyUtil.generateIdentifier("NCF", "board")
    // Slug.makeSlug lowercases and joins with underscore
    result should not be null
    result should be("ncf_board")
  }

  it should "return a slugified identifier that is all-lowercase" in {
    val result = TaxonomyUtil.generateIdentifier("MyFramework", "MyCategory")
    result should not be null
    result.forall(c => c.isLower || c == '_' || c == '-') shouldBe true
  }

  it should "return null when scopeId is blank" in {
    TaxonomyUtil.generateIdentifier("", "board") shouldBe null
  }

  it should "return null when scopeId contains only whitespace" in {
    TaxonomyUtil.generateIdentifier("   ", "board") shouldBe null
  }

  it should "return null when scopeId is null" in {
    TaxonomyUtil.generateIdentifier(null, "board") shouldBe null
  }

  it should "handle special characters in code by slugifying them" in {
    val result = TaxonomyUtil.generateIdentifier("NCF", "My Board")
    result should not be null
    // spaces become hyphens or underscores in slug
    result should startWith("ncf_")
  }

  // ─── getNextSequenceIndex ──────────────────────────────────────────────────

  "TaxonomyUtil.getNextSequenceIndex" should
    "return 1 when the node has no relations" in {
    val node = new Node()
    node.setIdentifier("ncf")
    // getOutRelations / getInRelations return empty list when null
    val result = TaxonomyUtil.getNextSequenceIndex(node)
    result shouldBe 1
  }

  it should "return 1 when node has relations of a different type" in {
    val node = new Node()
    node.setIdentifier("ncf")

    val rel = new Relation("ncf", "associatedTo", "ncf_board")
    val meta = new util.HashMap[String, Object]()
    meta.put("IL_SEQUENCE_INDEX", 5.asInstanceOf[Number])
    rel.setMetadata(meta)

    node.setOutRelations(util.Arrays.asList(rel))
    // "associatedTo" ≠ "hasSequenceMember", so should be ignored
    TaxonomyUtil.getNextSequenceIndex(node) shouldBe 1
  }

  it should "return max index + 1 when node has a single sequence-member relation" in {
    val node = new Node()
    node.setIdentifier("ncf")

    val rel = new Relation("ncf", RelationTypes.SEQUENCE_MEMBERSHIP.relationName(), "ncf_board")
    val meta = new util.HashMap[String, Object]()
    meta.put("IL_SEQUENCE_INDEX", 3.asInstanceOf[Number])
    rel.setMetadata(meta)

    node.setOutRelations(util.Arrays.asList(rel))
    TaxonomyUtil.getNextSequenceIndex(node) shouldBe 4
  }

  it should "return max(all indices) + 1 when node has multiple sequence-member relations" in {
    val node = new Node()
    node.setIdentifier("ncf")

    val rel1 = new Relation("ncf", RelationTypes.SEQUENCE_MEMBERSHIP.relationName(), "ncf_board")
    val meta1 = new util.HashMap[String, Object]()
    meta1.put("IL_SEQUENCE_INDEX", 2.asInstanceOf[Number])
    rel1.setMetadata(meta1)

    val rel2 = new Relation("ncf", RelationTypes.SEQUENCE_MEMBERSHIP.relationName(), "ncf_medium")
    val meta2 = new util.HashMap[String, Object]()
    meta2.put("IL_SEQUENCE_INDEX", 5.asInstanceOf[Number])
    rel2.setMetadata(meta2)

    val rel3 = new Relation("ncf", RelationTypes.SEQUENCE_MEMBERSHIP.relationName(), "ncf_grade")
    val meta3 = new util.HashMap[String, Object]()
    meta3.put("IL_SEQUENCE_INDEX", 1.asInstanceOf[Number])
    rel3.setMetadata(meta3)

    node.setOutRelations(util.Arrays.asList(rel1, rel2, rel3))
    // max index = 5, so next = 6
    TaxonomyUtil.getNextSequenceIndex(node) shouldBe 6
  }

  it should "default to index 1 when IL_SEQUENCE_INDEX is absent from metadata" in {
    val node = new Node()
    node.setIdentifier("ncf")

    val rel = new Relation("ncf", RelationTypes.SEQUENCE_MEMBERSHIP.relationName(), "ncf_board")
    // no IL_SEQUENCE_INDEX in metadata → defaults to 1
    node.setOutRelations(util.Arrays.asList(rel))
    // max = 1 (default), next = 2
    TaxonomyUtil.getNextSequenceIndex(node) shouldBe 2
  }

  it should "ignore inRelations whose startNodeId differs from the node identifier" in {
    val node = new Node()
    node.setIdentifier("ncf")

    // This is an inRelation: startNodeId = "ncf_board", not "ncf"
    val inRel = new Relation("ncf_board", RelationTypes.SEQUENCE_MEMBERSHIP.relationName(), "ncf")
    val meta = new util.HashMap[String, Object]()
    meta.put("IL_SEQUENCE_INDEX", 10.asInstanceOf[Number])
    inRel.setMetadata(meta)

    node.setInRelations(util.Arrays.asList(inRel))
    // startNodeId("ncf_board") ≠ node.getIdentifier("ncf"), so it's ignored
    TaxonomyUtil.getNextSequenceIndex(node) shouldBe 1
  }
}
