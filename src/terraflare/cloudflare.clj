(ns terraflare.cloudflare
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))


(def ^:private CF-EMAIL (System/getenv "CLOUDFLARE_EMAIL"))
(def ^:private CF-TOKEN (System/getenv "CLOUDFLARE_TOKEN"))

(def CF-BASE-URL "https://api.cloudflare.com/client/v4/")


(defn- send-api
  ([uri]
   (send-api uri {:page 1}))
  ([uri params]
   (when-let [response (client/get (str CF-BASE-URL uri)
                                   {:as           :json
                                    :headers      {"X-Auth-Email" CF-EMAIL
                                                   "X-Auth-Key"   CF-TOKEN}
                                    :query-params params})]
     (-> (:body response)
         (json/read-str :key-fn keyword)))))

(defn zone-id [domain]
  (when-let [response (send-api "zones")]
    (->> (:result response)
         (drop-while #(not= (:name %) domain))
         first
         :id)))


(defn dns-list [zone-id]
  (let [url (str "zones/" zone-id "/dns_records")]
    (when-let [body (send-api url)]
      (let [{:keys [page total_pages]} (:result_info body)]
        ; fetch to the end page
        (loop [records  (:result body)
               cur_page (inc page)]
          (if (<= cur_page total_pages)
            (let [body (send-api url {:page cur_page})]
              (recur (concat records (:result body)) (inc cur_page)))
            records))))))

(defn pagerules
  "https://api.cloudflare.com/#page-rules-for-a-zone-list-page-rules"
  [zone-id]
  (let [url (str "zones/" zone-id "/pagerules")]
    (when-let [body (send-api url)]
      (:result body))))