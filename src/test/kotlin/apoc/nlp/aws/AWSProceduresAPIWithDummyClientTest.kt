package apoc.nlp.aws

import apoc.util.TestUtil
import junit.framework.Assert.assertTrue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.neo4j.test.rule.ImpermanentDbmsRule
import java.util.stream.Collectors


class AWSProceduresAPIWithDummyClientTest {
    companion object {
        val apiKey: String? = "dummyKey"
        val apiSecret: String? = "dummyValue"

        @ClassRule
        @JvmField
        val neo4j = ImpermanentDbmsRule()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            TestUtil.registerProcedure(neo4j, AWSProcedures::class.java)
            assumeTrue(apiKey != null)
            assumeTrue(apiSecret != null)
        }

    }

    @Test
    fun `should extract entities`() {
        neo4j.executeTransactionally("""CREATE (a:Article {body:${'$'}body, id: 1})""", mapOf("body" to "dummyText"))
        neo4j.executeTransactionally("""CREATE (a:Article {body:${'$'}body, id: 2})""", mapOf("body" to "dummyText"))

        neo4j.executeTransactionally("""
                    MATCH (a:Article) WITH a ORDER BY a.id
                    WITH collect(a) AS articles
                    CALL apoc.nlp.aws.entities.stream(articles, {
                      key: ${'$'}apiKey,
                      secret: ${'$'}apiSecret,
                      nodeProperty: "body",
                      unsupportedDummyClient: true
                    })
                    YIELD value
                    RETURN value
                """.trimIndent(), mapOf("apiKey" to apiKey, "apiSecret" to apiSecret)) {
            val row1 = it.next()
            val value = row1["value"] as Map<*, *>
            val entities = value["entities"] as List<*>

            assertThat(entities, hasItem(mapOf("beginOffset" to null, "endOffset" to null, "score" to null, "text" to "token-1-index-0-batch-0", "type" to "Dummy")))
            assertThat(entities, hasItem(mapOf("beginOffset" to null, "endOffset" to null, "score" to null, "text" to "token-2-index-0-batch-0", "type" to "Dummy")))

            assertTrue(it.hasNext())

            val row2 = it.next()
            val value2 = row2["value"] as Map<*, *>
            val entities2 = value2["entities"] as List<*>

            assertThat(entities2, hasItem(mapOf("beginOffset" to null, "endOffset" to null, "score" to null, "text" to "token-1-index-1-batch-0", "type" to "Dummy")))
            assertThat(entities2, hasItem(mapOf("beginOffset" to null, "endOffset" to null, "score" to null, "text" to "token-2-index-1-batch-0", "type" to "Dummy")))
        }
    }

    @Test
    fun `should extract in batches`() {
        neo4j.executeTransactionally("""CREATE (a:Article2 {body:${'$'}body, id: 1})""", mapOf("body" to "dummyText"))

        neo4j.executeTransactionally("""
                    UNWIND range(1, 26) AS index
                    MATCH (a:Article2) WITH a ORDER BY a.id
                    WITH collect(a) AS articles
                    CALL apoc.nlp.aws.entities.stream(articles, {
                      key: ${'$'}apiKey,
                      secret: ${'$'}apiSecret,
                      nodeProperty: "body",
                      unsupportedDummyClient: true
                    })
                    YIELD value
                    RETURN collect(value) AS values
                """.trimIndent(), mapOf("apiKey" to apiKey, "apiSecret" to apiSecret)) {
            val row = it.next()
            val value = row["values"] as List<*>

            val allEntities = value.stream().flatMap { v -> ((v as Map<*, *>)["entities"] as List<*>).stream() }.collect(Collectors.toList())

            // assert that we have entries from the 2nd batch
            assertThat(allEntities, hasItem(mapOf("beginOffset" to null, "endOffset" to null, "score" to null, "text" to "token-1-index-0-batch-1", "type" to "Dummy")))
            assertThat(allEntities, hasItem(mapOf("beginOffset" to null, "endOffset" to null, "score" to null, "text" to "token-2-index-0-batch-1", "type" to "Dummy")))
        }
    }

}

