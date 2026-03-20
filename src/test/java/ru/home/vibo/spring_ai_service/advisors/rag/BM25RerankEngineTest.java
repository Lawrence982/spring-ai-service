package ru.home.vibo.spring_ai_service.advisors.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BM25RerankEngineTest {

    private BM25RerankEngine engine;

    @BeforeEach
    void setUp() {
        engine = BM25RerankEngine.builder().build();
    }

    @Test
    void rerank_nullCorpus_returnsEmptyList() {
        List<Document> result = engine.rerank(null, "query", 5);
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void rerank_emptyCorpus_returnsEmptyList() {
        List<Document> result = engine.rerank(List.of(), "query", 5);
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void rerank_singleDocument_returnsThatDocument() {
        Document doc = new Document("The tanuki is a magical creature from Golarion.");
        List<Document> result = engine.rerank(List.of(doc), "tanuki", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo(doc.getText());
    }

    @Test
    void rerank_limitLessThanCorpusSize_returnsExactlyLimit() {
        List<Document> corpus = List.of(
                new Document("Document about elves in fantasy worlds"),
                new Document("Document about dwarves and their mines"),
                new Document("Document about wizards casting spells"),
                new Document("Document about dragons and treasure"),
                new Document("Document about knights and honor")
        );

        List<Document> result = engine.rerank(corpus, "fantasy creatures", 3);

        assertThat(result).hasSize(3);
    }

    @Test
    void rerank_limitGreaterThanCorpusSize_returnsAllDocuments() {
        List<Document> corpus = List.of(
                new Document("First document about Golarion lore"),
                new Document("Second document about Pathfinder races")
        );

        List<Document> result = engine.rerank(corpus, "Golarion", 10);

        assertThat(result).hasSize(2);
    }

    @Test
    void rerank_russianQuery_ranksMostRelevantDocumentFirst() {
        Document relevantDoc = new Document(
                "Тануки — магические существа из мира Голариона, обладающие способностью к трансформации."
        );
        Document irrelevantDoc = new Document(
                "Гномы являются древними жителями подземелий и превосходными кузнецами."
        );
        Document anotherDoc = new Document(
                "Эльфы обитают в лесах и известны своими магическими способностями."
        );

        List<Document> corpus = List.of(irrelevantDoc, anotherDoc, relevantDoc);
        List<Document> result = engine.rerank(corpus, "тануки магические существа", 3);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getText()).isEqualTo(relevantDoc.getText());
    }

    @Test
    void rerank_englishQuery_ranksMostRelevantDocumentFirst() {
        Document relevantDoc = new Document(
                "The detective investigates mysteries and solves criminal cases in the city."
        );
        Document irrelevantDoc = new Document(
                "Dragons hoard gold and treasure in their mountain caves."
        );
        Document anotherDoc = new Document(
                "Wizards study arcane magic in their towers for many years."
        );

        List<Document> corpus = List.of(irrelevantDoc, anotherDoc, relevantDoc);
        List<Document> result = engine.rerank(corpus, "detective investigates mysteries", 3);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getText()).isEqualTo(relevantDoc.getText());
    }

    @Test
    void rerank_higherTermFrequency_scoredHigher() {
        // Document with the query word repeated many times should score higher
        Document highFreqDoc = new Document(
                "tanuki tanuki tanuki tanuki tanuki tanuki tanuki tanuki magical creature"
        );
        Document lowFreqDoc = new Document(
                "The tanuki is one of many creatures in the world of Golarion with various abilities."
        );

        List<Document> corpus = List.of(lowFreqDoc, highFreqDoc);
        List<Document> result = engine.rerank(corpus, "tanuki", 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo(highFreqDoc.getText());
    }

    @Test
    void rerank_queryWithNoTermOverlap_returnsLimitResults() {
        // Even when there is no term overlap, rerank should return up to limit results
        Document doc1 = new Document("Apple banana cherry dessert recipe food");
        Document doc2 = new Document("Elephant giraffe zebra savanna wildlife Africa");
        Document doc3 = new Document("Algebra calculus mathematics equations formula");

        List<Document> corpus = List.of(doc1, doc2, doc3);
        // Query about tanuki but corpus has no such term — all docs score 0.0
        List<Document> result = engine.rerank(corpus, "tanuki magical shape-shifter", 2);

        assertThat(result).hasSize(2);
    }
}
