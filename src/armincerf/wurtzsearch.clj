(ns armincerf.wurtzsearch
  (:require [net.cgrand.enlive-html :as html]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.data :as java]
            [clojure.java.io :as io])
  (:import [com.algolia.search Defaults DefaultSearchClient]
           com.algolia.search.models.settings.IndexSettings)
  (:gen-class))

(def latest-time (atom nil))

(defn parse-time-string
  [s]
  (let [time-string (-> s
                        (str/replace #" " "")
                        (str/replace #"\n" " "))]
    (try (.parse (java.text.SimpleDateFormat. "MM.dd.yy hh:mm a") time-string)
         (catch Exception e
           (println "Couldn't parse " s)))))

(defn question-text
  [question]
  (def question question)
  (let [question-el (some-> question
                            :content
                            vec
                            (get-in [3 :content])
                            first)]
    (cond
      ;; Question contains a link to 'similar questions' page
      (map? question-el)
      {:link (str "https://billwurtz.com/questions/" (get-in question-el [:attrs :href]))
       :text (first (:content question-el))}
      ;; Question is a normal string
      (string? question-el)
      (str/trim question-el)
      ;else return nothing
      :else
      nil)))

(defn question-time
  [question]
  (def question question)
  (some-> question
          :content
          vec
          (get-in [1 :content])
          first
          parse-time-string))

(defn format-content
  [[question answer]]
  (let [timestamp (some->> (question-time question)
                           (reset! latest-time))]
    (when-let [question-text (question-text question)]
      {:question question-text
       :timestamp @latest-time
       :answer (when (string? answer)
                 (-> answer
                     str/trim
                     (str/replace #" " "")))})))

(defn content-for-date
  [raw-questions]
  (let [;; questions have no html structure that can be used to parse things
        ;; properly so have to use some magic numbers and partition into
        ;; [question answer] format... not pretty but the structure of the site
        ;; has never changed so probably not a big issue
        content (->> 6
                     (nth (html/select raw-questions [:body]))
                     :content
                     (drop 11)
                     (partition 2))]
    (->> (map format-content content)
         (remove nil?))))

(def current-year (-> "yyyy" java.text.SimpleDateFormat. (.format (new java.util.Date))))

(defn process-url
  [url-string index]
  (let [raw-questions (try (-> url-string
                               java.net.URL.
                               html/html-resource)
                           (catch Exception e
                             (println "No content found..")))]
    (when raw-questions
      (println "Uploading to algolia")
      (.saveObjects index (content-for-date raw-questions) true))))

(defn -main
  [& args]
  (let [algolia-client (DefaultSearchClient/create (first args) (second args))
        index (.initIndex algolia-client "bill questions")]
    (.setSettings index (-> (IndexSettings.)
                            (.setAttributeForDistinct "foo")
                            (.setDistinct true)))
    (println algolia-client index)
    (process-url "https://billwurtz.com/questions/questions.html" index)
    ;; backfill previous questions
    #_(doseq [year (range 2016 (inc (Integer/parseInt current-year)))]
      (doseq [month (map inc (range 12))
              :let [date-str (str year "-" (format "%02d" month))
                    url-string (str "https://billwurtz.com/questions/questions-" date-str ".html")]]
        (println "Processing " url-string " to index " index)
        (process-url url-string index)))))

(comment
  ;;throwaway account api key, may not work anymore
  (def client (DefaultSearchClient/create "XMYY7X6YSY" "020a2412365c6bad1a4ff56f4ae3b9ae"))
  (def index (.initIndex client "questions"))
  (java/from-java (.saveObject index {:question "TEST TEST TEST" :timestamp #inst "2020" :answer "ANSWER"} true)))
