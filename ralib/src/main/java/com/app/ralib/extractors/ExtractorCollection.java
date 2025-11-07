package com.app.ralib.extractors;

import android.util.Log;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public class ExtractorCollection {
    private static final String TAG = "ExtractorCollection";
    public interface ExtractionListener {
        void onProgress(String message, float progress, HashMap<String, Object> state);
        void onComplete(String message, HashMap<String, Object> state);
        void onError(String message, Exception ex, HashMap<String, Object> state);
    }

    public interface IExtractor {
        void setSourcePath(Path sourcePath);
        void setDestinationPath(Path destinationPath);
        void setExtractionListener(ExtractionListener listener);
        void setState(HashMap<String, Object> state);
        HashMap<String, Object> getState();
        boolean extract();
    }

    public static class Builder {
        private ExtractorCollection extractorCollection = new ExtractorCollection();
        private HashMap<String, Object> state = new HashMap<>();

        public Builder configureState(String key, Object value) {
            this.state.put(key, value);
            return this;
        }

        public Builder addExtractor(IExtractor extractor) {
            return addExtractor(extractor, true);
        }

        public Builder addExtractor(IExtractor extractor, boolean isInjectState) {
            if (isInjectState) {
                extractor.setState(state);
            }
            extractorCollection.addExtractor(extractor);
            return this;
        }

        public ExtractorCollection build() {
            return extractorCollection;
        }
    }

    private final ArrayList<IExtractor> extractors = new ArrayList<>();

    public void addExtractor(IExtractor extractor) {
        extractors.add(extractor);
    }

    public void extractAllInNewThread() {
        new Thread(() -> {
            try {
                int count = 0;
                for (IExtractor extractor : extractors) {
                    Log.d(TAG, "Starting extraction for extractor: " + extractor.getClass().getSimpleName() + " (" + (++count) + "/" + extractors.size() + ")");
                    extractor.getState()
                            .put("extractor_index", count++);
                    boolean success = extractor.extract();
                    if (!success) {
                        Log.e(TAG, "Extraction failed for extractor: " + extractor.getClass().getSimpleName());
                        break;
                    }
                }
            }
            catch (Exception ex) {
                Log.e(TAG, "Extraction error: ", ex);
            }
        }).start();
    }
}
