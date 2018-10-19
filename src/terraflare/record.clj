(ns terraflare.record
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(def CF-EMAIL (System/getenv "CLOUDFLARE_EMAIL"))
(def CF-TOKEN (System/getenv "CLOUDFLARE_TOKEN"))

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


(defn rename-key
  "Rename key from k1 to k2 preserving metadata."
  [m k1 k2]
  (if (not= k1 k2)
    (-> m
        (assoc k2 (get m k1))
        (dissoc k1))
    m))


(defn- records->resources [dns-records]
  (let [gen-name   (fn [m]
                     (-> (str (:type m) "-" (:name m) "-")
                         (str/replace "." "_")
                         (str/replace "*" "STAR")
                         (gensym)                           ; avoid name collision
                         ))

        preprocess (fn [m]
                     (let [default?  (fn [k v]
                                       (case k
                                         :ttl (= v 1)
                                         :proxied (= v false)
                                         false))
                           trim-name (fn [v]
                                       (let [postfix (str "." (:zone_name m))
                                             postfix (str (str/replace postfix "." "\\.") "$")]
                                         (first (str/split v (re-pattern postfix)))))]
                       (-> (select-keys m [:type :name :content :ttl :priority :proxied])
                           (dissoc-with (comp not default?))
                           (rename-key :content :value)
                           (update :name trim-name)
                           (assoc :domain "${var.domain}"))))

        convert    (fn [m]
                     {:id     (:id m)
                      :record (preprocess m)
                      :name   (gen-name m)})
        ]
    (map convert dns-records)))


(defn- resources->plan [domain resources]
  (let [template  {:provider {:cloudflare {}}
                   :variable {:domain {:default domain}}
                   :resource {:cloudflare_record {}}}

        resources (->> resources
                       (map #(hash-map (:name %) (:record %)))
                       (into {}))]
    (assoc-in template [:resource :cloudflare_record] resources)))


(defn- resources->import
  "Build `terraform import cloudflare_record.${name} ${domain}/${id}` form."
  [domain resources]
  (let [cmds (->> resources
                  (map #(vector "terraform"
                                "import"
                                (str "cloudflare_record." (:name %))
                                (str domain "/" (:id %)))))]
    (doseq [cmd cmds]
      (apply println cmd))))


(defn tf-json-from [domain]
  (let [zone-id     (cf-zone-id domain)
        dns-records (cf-dns-list zone-id)
        resources   (records->resources dns-records)]

    ;; write json file
    (->> resources
         (resources->plan domain)
         (json/write-str)
         (spit (str domain ".json")))

    ;; print import command
    (resources->import domain resources)

    ))


(defn -main [& args]
  (doseq [domain args]
    (tf-json-from domain)))


(comment

  (def domain "ridi.io")
  @(def zone-id (cf-zone-id domain))

  @(def dns-records (cf-dns-list zone-id))

  @(def resources (records->resources dns-records))

  (resources->plan domain resources)
  (resources->import domain resources)

  )
