package com.example.vocabmaster.data.remote;

import java.util.List;

public class DictionaryResponse {
    public String word;
    public String phonetic;
    public List<Phonetic> phonetics;
    public List<Meaning> meanings;

    public static class Phonetic {
        public String text;
        public String audio;
    }

    public static class Meaning {
        public String partOfSpeech;
        public List<Definition> definitions;
    }

    public static class Definition {
        public String definition;
        public String example;
        public List<String> synonyms;
        public List<String> antonyms;
    }
}
