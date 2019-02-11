(ns terraflare.pagerules
  (:require [terraflare.cloudflare :as cf]
            [clojure.data.json :as json]))

(defn rule->resource [rule]
  {:id       (:id rule)
   :zone     "${var.domain}"
   :target   (get-in (first (:targets rule)) [:constraint :value])
   :priority (:priority rule)
   :actions  (reduce (fn [m action]
                       (let [k (:id action)
                             v (cond
                                 ; boolean type properties
                                 (#{"always_use_https"
                                    "disable_apps"
                                    "disable_performance"
                                    "disable_railgun"
                                    "disable_security"} k)
                                 (or (nil? (:value action)) (:value action))

                                 :else
                                 (:value action))]
                         (assoc m k v)))
                     {}
                     (:actions rule))
   :status   (:status rule)})

(defn genname [resource]
  (str "rule-" (:priority resource)))

(defn- resources->plan [domain resources]
  (let [template {:provider {:cloudflare {}}
                  :variable {:domain {:default domain}}
                  :resource {:cloudflare_page_rule {}}}]
    (->> resources
         (reduce #(assoc %1 (genname %2) (dissoc %2 :id)) {})
         (assoc-in template [:resource :cloudflare_page_rule]))))

(defn- resources->import [domain resources]
  (let [cmds (->> resources
                  (map #(vector "terraform"
                                "import"
                                (str "cloudflare_page_rule." (genname %))
                                (str domain "/" (:id %)))))]
    (doseq [cmd cmds]
      (apply println cmd))))

(defn- tf-json-from [domain]
  (let [zone-id   (cf/zone-id domain)
        pagerules (cf/pagerules zone-id)
        resources (map rule->resource pagerules)]

    (->> resources
         (resources->plan domain)
         (json/write-str)
         (spit (str domain "-pagerules.json")))

    ;; print import command
    (resources->import domain resources)))

(defn -main [& args]
  (doseq [domain args]
    (tf-json-from domain)))

(comment

  (def domain "ridicorp.com")
  @(def zone-id (cf/zone-id domain))

  @(def pagerules (cf/pagerules zone-id))

  (resources->plan domain (map rule->resource pagerules))
  (resources->import domain (map rule->resource pagerules))

  )
