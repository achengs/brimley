(ns brimley.util)

(def absent
  "special unique value to signify a key was not present (vs set to nil)"
  (Object.))

(defn absent?
  "return true if x was absent. use with get and get-in"
  [x]
  (= absent x))

(defn deref-and-validate
  "throw if the menu is malformed.
  otherwise return the current choices and path."
  [ctx-atm]
  (let [ctx @ctx-atm]
    (when (or (nil? ctx)
              (not (map? ctx)))
      (throw (ex-info "brimley: context is not a map"
                      {:type (type ctx), :context ctx})))
    (let [choices (get-in ctx [:brimley :choices] absent)
          path    (get-in ctx [:brimley :path]    absent)]
      (when (absent? choices) (throw (ex-info "brimley: no choices found in context"      ctx)))
      (when (absent? path)    (throw (ex-info "brimley: no current path found in context" ctx)))
      [choices path])))

(defn keys-in
  "returns the paths to all the leaf node keys in map m.
  https://stackoverflow.com/a/21769786 -- thank you Alex."
  [m]
  (if (map? m)
    (vec
      (mapcat (fn [[k v]]
                (let [sub    (keys-in v)
                      nested (map #(into [k] %) (filter (comp not empty?) sub))]
                  (if (seq nested)
                    nested
                    [[k]])))
              m))
    []))

(defn leaves-in [m]
  (doall(map #(get-in m %) (keys-in m))))
