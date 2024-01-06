(ns main
  (:require [babashka.pods :as pods])
  (:require [babashka.http-client :as http])
  (:require [cheshire.core :as json])
  (:require [clojure.string :as str]))

(import [java.net URLEncoder]) 

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(def base "https://www.goodreads.com/quotes/search?commit=Search&page=1")
(require '[pod.retrogradeorbit.bootleg.utils :as scrape]
         '[pod.retrogradeorbit.hickory.select :as s])

(defn link->tree [url] 
  (-> (http/get url) ; can use slurp but its a bit slower i think
        :body 
        (scrape/convert-to :hickory-seq)))

(defn filter-quotetext [tree] 
  (let [quote-text (s/select (s/class "quoteText") tree)] 
    (->> quote-text first :content
         (map (fn [el] (if (= (:tag el) :br) "\n" el)))
         (group-by string?))))

(defn -main [& args] 
  (let [baseurl (str base "&q=" (URLEncoder/encode (first args)) "&utf8=%E2%9C%93")
        tree (link->tree baseurl)]
    (-> (->> tree 
        (filter #(= (:type %) :element))
        (mapcat #(->> % 
                  (s/select (s/descendant (s/class "quoteDetails")))))
        (map (fn [quote] 
               (let [quote-text (filter-quotetext quote)] 
               { :image (->> (s/select (s/class "quoteAvatar") quote) 
                                       first (s/select (s/tag :img))
                                       first :attrs :src) 
                 :quote (str/trim  (str/join (get quote-text true)))
                 :authorOrTitle (let [[author book] (get quote-text false)]  
                                  {:author (str/trim (first (:content author))) 
                                   :title (->> (s/select (s/tag :a) book) first :content first)})
              })))
        (map #(merge (:authorOrTitle %) (dissoc % :authorOrTitle))))
        (json/generate-string { :pretty true })
        (print))))
