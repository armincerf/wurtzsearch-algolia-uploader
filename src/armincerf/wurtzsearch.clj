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

(defn part->html
  [content]
  (str/join
   (for [part content]
     (if (map? part)
       (let [tag (some-> part :tag name)]
         (str "<" tag
              (when-let [href (get-in part [:attrs :href])]
                (str" href='" href "'"))
              ">"
              (str/join (:content part))
              "</" tag ">"))
       part))))

(defn question-text
  [question]
  (let [question-el (some-> question
                            (html/select [:qco])
                            first
                            :content)]
    (part->html question-el)))

(defn question-time
  [question]
  (some-> question
          (html/select [:dco])
          first
          :content
          str/join
          parse-time-string))

(defn format-content
  [[question answer]]
  (let [timestamp (some->> (question-time question)
                           (reset! latest-time))]
    (when-let [question-text (question-text question)]
      {"question" question-text
       "timestamp" @latest-time
       "answer" (when (seq answer)
                 (part->html answer))})))

(defn content-for-date
  [raw-questions]
  (let [;; questions have no html structure that can be used to parse things
        ;; properly so have to use some fairly fragile code here Basically we
        ;; select only h3s, strings and a tags in the root of the body, get rid
        ;; of the 'static' a tags that aren't questions or answers, partition by
        ;; h3 tags as an h3 always designates the start of a new question, and
        ;; then split into [question answer] so we can process it
        content (->> (html/select raw-questions #{[:body :> :h3] [:body :> html/text-node] [:body :> :a]})
                     (remove #(and (string? %) (< (.length (str/trim %)) 4)))
                     (remove #(= "MORE RECENT QUESTIONS" (first (:content %))))
                     (remove #(= "bottom" (get-in % [:attrs :name])))
                     (remove #(= "PREVIOUS QUESTIONS" (first (:content %))))
                     (partition-by #(= :h3 (:tag %)))
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
    (def raw-questions raw-questions)
    (when raw-questions
      (println "Uploading to algolia")
      (.saveObjects index (content-for-date raw-questions) true))))

(defn -main
  [& args]
  (let [algolia-client (DefaultSearchClient/create (first args) (second args))
        index (.initIndex algolia-client "bill questions")]
    (process-url "https://billwurtz.com/questions/questions.html" index)
    ;; backfill previous questions
    (doseq [year (range 2016 (inc (Integer/parseInt current-year)))]
      (doseq [month (map inc (range 12))
              :let [date-str (str year "-" (format "%02d" month))
                    url-string (str "https://billwurtz.com/questions/questions-" date-str ".html")]]
        (println "Processing " url-string " to index " index)
        (process-url url-string index)))))
(-main "XMYY7X6YSY" "020a2412365c6bad1a4ff56f4ae3b9ae")
(comment
  ;;throwaway account api key, may not work anymore
  (def client (DefaultSearchClient/create "XMYY7X6YSY" "020a2412365c6bad1a4ff56f4ae3b9ae"))
  (def index (.initIndex client "questions"))
  (java/from-java (.saveObject index {:question "TEST TEST TEST" :timestamp #inst "2020" :answer "ANSWER"} true)))
