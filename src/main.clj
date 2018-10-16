(ns terraflare
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

(def CF-EMAIL "")
(def CF-TOKEN "")
(def CF-BASE-URL "https://api.cloudflare.com/client/v4/")

(defn- cf-send-api
  ([uri]
   (cf-send-api uri {:page 1}))
  ([uri params]
   (when-let [response (client/get (str CF-BASE-URL uri)
                                   {:as           :json
                                    :headers      {"X-Auth-Email" CF-EMAIL
                                                   "X-Auth-Key"   CF-TOKEN}
                                    :query-params params})]
     (-> (:body response)
         (json/read-str :key-fn keyword)))))

(defn cf-zone-id [domain]
  (when-let [response (cf-send-api "zones")]
    (->> (:result response)
         (drop-while #(not= (:name %) domain))
         first
         :id)))

(defn cf-dns-list [zone-id]
  (let [url (str "zones/" zone-id "/dns_records")]
    (when-let [body (cf-send-api url)]
      (let [{:keys [page total_pages]} (:result_info body)]
        ; fetch to the end page
        (loop [records  (:result body)
               cur_page (inc page)]
          (if (<= cur_page total_pages)
            (let [body (cf-send-api url {:page cur_page})]
              (recur (concat records (:result body)) (inc cur_page)))
            records))))))

(defn dissoc-with
  "Dissociate map with predicate preserving metadata"
  [m f]
  (reduce-kv (fn [m k v]
               (if (f k v)
                 m
                 (dissoc m k)))
             m m))

(defn rename-key [m k1 k2]
  (-> m
      (assoc k2 (get m k1))
      (dissoc k1)))

(defn preprocess-record [record]
  (let [default? (fn [k v]
                   (case k
                     :ttl (= v 1)
                     :proxied (= v false)
                     false))]
    (-> record
        (dissoc-with (comp not default?))
        (rename-key :content :value)
        (assoc :domain "${var.domain}"))))

(defn dns->hcl [domain dns-records]
  (let [scaffold  {:provider {:cloudflare {}}
                   :variable {:domain {:default domain}}
                   :resource {:cloudflare_record {}}}

        ; avoid name collision
        gen-name  (fn [m]
                    (-> (str (:type m) "-" (:name m) "-")
                        (clojure.string/replace "." "_")
                        (gensym)))

        resources (->> dns-records
                       (map #(select-keys % [:type :name :content :ttl :priority :proxied]))

                       ; json2hcl couldn't handle escaped json
                       #_(remove #(= (:type %) "TXT"))

                       (map preprocess-record)

                       (reduce (fn [m v] (assoc m (gen-name v) v)) {})
                       )
        ]
    (assoc-in scaffold [:resource :cloudflare_record] resources)))


(defn dns->json [domain out]
  (let [zone-id     (cf-zone-id domain)
        dns-records (cf-dns-list zone-id)]
    (spit out (json/write-str (dns->hcl domain dns-records)))))

(comment

  (def domain "ridicorp.com")
  @(def zone-id (cf-zone-id domain))

  @(def dns-records (cf-dns-list zone-id))

  @(def resources (dns->hcl domain dns-records))

  (let [domains ["initialcoms.com"
                 "ridi.com"
                 "ridicorp.com"
                 "ridi.io"
                 "ridi.kr"
                 "ridibooks.com"
                 "ridishop.com"
                 "studiod.co.kr"]]
    (doseq [domain domains
            :let [json (str domain ".json")]]
      (dns->json domain json)

      (println (str "json2hcl < " json " > " domain ".tf"))
      ))

  )
