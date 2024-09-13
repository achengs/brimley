# Brimley

[![Clojars Project](https://img.shields.io/clojars/v/com.github.achengs/brimley.svg)](https://clojars.org/com.github.achengs/brimley)

This Clojure library parses an EDN file to create a menu tree for use in a terminal.
Clone [brimley-demo](https://github.com/achengs/brimley-demo)
to experiment with it locally.

Brimley's appearance and behavior are configurable.

Brimley can call your existing functions without requiring
any change to their current signatures, in most cases.
(If you find a counterexample, please let me know!)

Each menu option is displayed as an abbreviation and a description.
The user types an abbreviation to choose an option.

An option can either cause an action to be performed
(which could be a call to one of your existing functions),
or move the user to a sub-menu.

A tree of menus is like a directory tree on a file system,
so the user has a current path.

Below is an example menu with 3 options,
the user's current path,
and a prompt for their choice.

```
  e |                           Specify email
env |                  Environment (sub-menu)
 cp | Copy to clipboard operations (sub-menu)
Your current menu path:
/
To quit, Control-D in a terminal or ESC in a REPL. Your choice:
```

Menu options can call your functions
with arguments from any path within a _context atom_ map.
Arguments can also be hard-coded literal values in the EDN file
or any mix of the two. You can instruct Brimley to perform
substitutions in a map before passing it as an argument.

You're able to put any of your existing atoms inside the context atom,
which means menu options can participate in your program's state.

While you will probably not need to change your _existing_ code to use this menu,
these two requirements must be true of your new code:
1. The functions called by your menu options need to be ns-resolvable
   when you use Brimley to load your EDN file.
   - Otherwise the load throws an exception.
   - To work around this requirement,
     you could build (or add to) a menu programmatically,
     by updating the context atom before passing it to Brimley.
1. Any atoms you want Brimley to pass to your functions need to be
   included at paths of your chosing* inside the context atom.
   - *But paths starting with `:brimley` are reserved.
   - A path miss means `nil` is the argument's value.
     - TODO: perhaps this should be configurable to throw instead of passing `nil`
     - TODO: or configurable on a case-by-case basis to pass an alternate value.

When you load a configuration EDN file with `load-menu!`,
the first argument is the _context atom_ containing a map.
Brimley makes updates to the context atom's map at path `[:brimley]`.

You can put anything you want at any other path,
including atoms from your program.
You can do this before or after the load,
as long as it's before you need Brimley to pass them as arguments.

You can configure Brimley to pass one of your atoms to your function as-is,
or pass the content of your derefenced atom,
or pass a value at a path from within your atom.

The logic in the previous paragraph applies
to all the leaves in a map, if one or more of your function's arguments
is a map.

Therefore it's likely you won't need to change your function signatures
to make them call-able from Brimley.

## Features

- You can configure implicit (not displayed) menu choices
  that are available at every path in the menu tree,
  like `..` for backing out of a sub-menu.
- You can configure what happens after every action chosen by the user
  (e.g. print the current date and time)
- You can configure the content and appearance of these:
  - the current valid menu options
  - the current path
  - the user prompt
- Brimley can call your code and participate in your state
  with no changes to your existing code.

## Usage

### Configuration Syntax

Here is an example configuration file. Below it is an explanation.

```
[""     brimley.tasks/show-choices
 ".."   brimley.tasks/back-one!
 "!" brimley.tasks/toggle-mode!

 "e" ["Specify email"        pst.core/start-with-email     [[:subst :state]]]
 "E" ["Reload current email" pst.core/reload-current-email [[:subst :state]]]

 "env"
 ["Environment"
  ["l" ["use local" pst.core/set-env  ["local" [:subst :env]]]
   "d" ["use dev"   pst.core/set-env  ["dev"   [:subst :env]]]
   "s" ["use stage" pst.core/set-env  ["stage" [:subst :env]]]
   "p" ["use prod"  pst.core/set-env  ["prod"  [:subst :env]]]
   "." ["show"      pst.core/show-env
        [[:deref :env]
         {:env-atm [:subst :env]
          :last    [:subst :brimley :last-result]
          :foo     {:email  [:dref2 [:state] [:email]]
                    :literal 42}}]]]
```

Overall, a menu is a vector of pairs:
1. The first of each pair is a string: the abbreviation that the user would type to pick the corresponding option.
1. The second of each pair can be one of three* things:
   1. A single ns-resolvable symbol for a function, in which case the pair describes a menu option that is valid at every path within the menu tree. Displaying them at every path in the menu tree is repetitive, so we don't. The first three pairs in the code block above are examples of these always-available hidden menu options:
      1. Just hit Enter by itself to display the current choices.
      1. Type `..` and hit Enter to back out of the current sub-menu to its parent.
      1. Type `!` and Enter to toggle between novice and expert mode. One way to know what that means is to try this library :)
   1. A vector of two elements (a description and another menu). Above, you can see `env` leads to a sub-menu called `Environment`. Brimley does not impose any limits on the number or depth of sub-menus.
   1. A vector of three elements (a description, a ns-resolvable symbol for a function, an argument list), in which case the pair describes a menu option that will call the function after modifying the arguments by peforming any configured substitutions. Here are the supported substitutions:
      1. `:subst` -- The first example above of this kind calls `pst.core/start-with-email` when the user types `e` and hits Enter. `start-with-email` takes one argument. This notation `[:subst :state]` tells Brimley to deref the context atom, go to path `[:state]`, and substitute the value at that path as the argument to `start-with-email`. You see, the engineer who wrote `pst.core` has his own state atom, and he stuck it inside the context atom at path `[:state]`. So `start-with-email` is able to prompt the user for an email address and update `pst.core`'s state atom. Here, `:state` is at the top level, but deep paths are supported.
      1. literal -- The first example above of this kind is when the user enters the `Environment` sub-menu with `env`, and then hits `l` to use the local environment. (`pst` is a prod support tool and the user wants to test something in a lower environment first.) Brimley calls `pst.core/set-env` with two arguments. The first argument is the literal string `"local"`. The second argument is a `:subst`. You see, `pst.core` has a second atom that contains the environment. Brimley supports argument lists that contain any mix of literal values and configured substitutions from the context atom.
      1. `:deref` -- The only example above of this kind is when the user is in the `Environment` sub-menu and picks option `.` because the user wants some info shown. Brimley calls `pst.core/show-env` with two arguments. The first argument is configured as `[:deref :env]` which tells Brimley to go into the context atom at path `[:env]` and expect the value at that path to be an atom. Brimley derefs this atom and passes it as the first argument to `pst.core/show-env`, because the function only needs to print the contents of the env atom which is a string; it does not need the atom that contains the string.
      1. `:dref2` -- The only example above of this kind is also for the call to `pst.core/show-env`. The second argument to `show-env` is a map, and inside this map at path `[:foo :email]` Brimley is told by this notation `[:dref2 [:state] [:email]]` to go into the context atom at path `[:state]`, expect the value at that path to be an atom, deref it, and substitute the value at path `[:email]`.
      1. substitutions inside map arguments - The example for `:dref2` also doubles as an example of the support for substitutions of values inside maps. So if your functions take maps as agruments, this library supports injecting values into those maps before calling your functions.

When Brimley calls a function, the result is stored in the context atom at path `[:brimley :last-result]`.
There's an example of this in the call to `pst.core/show-env`.

*You could extend Brimley to support something new besides the current three
(sub-menus, regular displayed options, always-available not-displayed options)
if ...
1. the second of your pair contains `n > 3` elements
1. and you define a multimethod variant of `brimley.parse/parse-menu-item` with a dispatch-val of `n`
1. and you update the spec in `brimley.parse`

## Usage

```
(ns your.namespace
  (:require
    [brimley.core :as brimley]
    ;; and for your functions to be ns-resolvable when you load:
    [your.ns1]
    [your.ns2]))

(def menu-context-atom
  (atom {:some arbitrary
         :possibly nested
         :map containing
         :values you
         :care about
         :including atoms
         :excluding-top-level-key :brimley}))

(brimley/load-menu! menu-context-atom "/your/menu-configuration.edn")

;; possibly customize brimley's appearance/behavior here

;; finally:
(brimley/loop-menu! menu-context-atom)

```

`load-menu!` can take either the two arguments you see above,
or two additional arguments:
- the list of tasks to perform for every prompt
- the list of tasks to perform after every action

See the customizations below for an explanation of those lists of tasks.

## Supported Customizations

### List Tasks To Do For Every Prompt
You can specify a list of tasks to perform every time we prompt the user.

The list can be any mix of ...
- functions (that all accept the context atom as their only argument)
- keywords (see the keys of brimley.tasks/keyword->task for the set of supported keywords)

For example, let's say you want to encourage your user
before showing the current choices.

```
(defn show-encouraging-quote [ctx-atm]
  (println "You got this!"))

(swap! ctx-atm assoc-in
       [:brimley :customizations :prompt-tasks]
       [show-encouraging-quote
        :show-choices
        :show-path
        :show-prompt])
```

### List Tasks To Do After Every Action
You can specify a list of tasks to perform after every action chosen by the user,
excluding entering and backing out of sub-menus.

The list can be any mix of ...
- functions (that all accept the context atom as their only argument)
- keywords (see the keys of brimley.tasks/keyword->task for the set of supported keywords)

For example, you might want to print the current date and time after every action.

```
(defn print-current-date-time [ctx-atm]
  ,,,)

(swap! ctx-atm assoc-in
       [:brimley :customizations :after-tasks]
       [:back-to-root!
        print-current-date-time])
```

### Customize Brimley's Appearance

| Aspect | Default Behavior | Multimethod | Arguments | Dispatch Keyword |
| --- | --- | --- | --- | --- |
| Distinction between menu and sub-menu | Append "(sub-menu)" | brimley.choices/format-submenu | [ctx-atm entry-name] | :format-submenu |
| Appearance of current valid choices | Abbrevation and Description separated by vertical bar | brimley.choices/format-choices | [ctx-atm] | :format-choices |
| Path components | Choice descriptions | brimley.path/get-path-component | [ctx-atm path] | :path-component |
| Path syntax | Path components separated by / | brimley.path/format-path | [ctx-atm component] | :format-path |
| Path as a string | /submenu1 description/submenu2 description | brimley.path/path->string | [ctx-atm] | :path->string |

To configure an Aspect,
1. Define a Multimethod variant with your own dispatch-val
1. Have the multimethod accept the specified Arguments. `ctx-atm` is the context atom.
1. Make the multimethod return a string.
1. Set your dispatch-val as the value for the Dispatch Keyword in the context map at `[:brimley :customizations]`

Example: to customize the distinct appearance of a sub-menu's description, define this multimethod in your repo:

```
(defmethod brimley.choices/format-submenu :i-prefer-dots
  [ctx-atm entry-name]
  (format "%s ..." entry-name))
```

Then set your dispatch-val `:i-prefer-dots` to be the value of `:format-submenu` at the right path:
```
(swap! ctx-atm update-in
       [:brimley :customizations]
       assoc :format-submenu :i-prefer-dots)
```

## Inspiration

The inspiration for Brimley is
Wilford Brimley's character Harold Smith
picking a single key to press
in the 1985 color motion picture
_Remo Williams: The Adventure Begins._

Alas, users of this menu must also press Enter.
