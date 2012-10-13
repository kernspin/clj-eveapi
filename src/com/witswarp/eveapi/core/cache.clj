;; EVE Manager - Character and corporation management for EVE Online
;; Copyright (C) 2011 Simon Jagoe

;; This program is free software: you can redistribute it and/or
;; modify it under the terms of version 3 of the GNU General Public
;; License as published by the Free Software Foundation.

;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.

;; You should have received a copy of the GNU General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.

(ns com.witswarp.eveapi.core.cache
  (:require [cupboard.core :as cb]
            [clj-time.core :as time-core]
            [clojure.java.io :as io]))

(cb/defpersist api-item
  ((:key :index :unique)
   (:data)))

(defn expired? [data]
  "Returns true if the provided api-item has expired and should be re-fetched"
  (if (nil? data)
    true
    (let [cached-until (first (filter #(= :cachedUntil (:tag %))
                                      (:content data)))]
      (if (time-core/after? (time-core/now) (first (:content cached-until)))
        true
        false))))

(defn make-key [host query-params path-args]
  "Creates a key suitable for storing items in the API cache"
  (let [account-id [(get query-params :keyID) (get query-params :characterID)]
        identifiers (apply conj (apply conj [host] account-id) path-args)]
    (apply str \/ (interpose \/ (filter #(not (nil? %)) identifiers)))))

(defn get-from-cache [cache-path key]
  "Fetches the item with specified key from the cache (or nil if it does not exist)"
  (if (not (nil? cache-path))
    (cb/with-open-cupboard [cache-path]
      (let [result (cb/retrieve :key key)]
        (if (not (nil? result))
          (:data result))))))

(defn delete-from-cache! [cache-path key]
  "Deletes the specified item from the cache"
  (if (not (nil? cache-path))
    (cb/with-open-cupboard [cache-path]
      (cb/with-txn []
        (cb/delete (cb/retrieve :key key))))))

(defn store-in-cache! [cache-path key result]
  "Adds the specified item to the cache with the given key"
  (if (not (nil? cache-path))
    (cb/with-open-cupboard [cache-path]
      (cb/with-txn []
        (let [old-result (cb/retrieve :key key)]
          (if (not (nil? old-result))
            (cb/delete old-result))
          (cb/make-instance api-item [key result]))))))

(defn init-cache! [cache-path]
  "Creates a cache at the specified path"
  (let [dummy "__DUMMY__"]
    (.mkdir (io/file cache-path))
    (cb/with-open-cupboard [cache-path]
      (cb/with-txn []
        (try
          (cb/retrieve :key dummy)
          (catch java.lang.RuntimeException e
            (cb/make-instance api-item [dummy ""])
            (cb/delete (cb/retrieve :key dummy))))))))

(defn -delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
Raise an exception if any deletion fails unless silently is true."
  [f & [silently]]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (-delete-file-recursively child silently)))
    (io/delete-file f silently)))

(defn clear-cache! [cache-path]
  "Clears all data from the specified cache"
  (-delete-file-recursively cache-path))
