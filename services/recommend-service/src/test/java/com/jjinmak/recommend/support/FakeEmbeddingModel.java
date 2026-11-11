package com.jjinmak.recommend.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1536;

    private final List<String> requestedTexts = new CopyOnWriteArrayList<>();
    private final Set<String> failingTexts = ConcurrentHashMap.newKeySet();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        for (String text : request.getInstructions()) {
            requestedTexts.add(text);
            if (failingTexts.contains(text))
                throw new IllegalStateException("임베딩 호출 실패: " + text);
            embeddings.add(new Embedding(vectorOf(text), embeddings.size()));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        throw new UnsupportedOperationException();
    }

    public static float[] vectorOf(String text) {
        float[] vector = new float[DIMENSIONS];
        Arrays.fill(vector, 1f);
        vector[0] = text.length();
        return vector;
    }

    public List<String> requestedTexts() {
        return List.copyOf(requestedTexts);
    }

    public void failOn(String text) {
        failingTexts.add(text);
    }

    public void reset() {
        requestedTexts.clear();
        failingTexts.clear();
    }
}
