# Caffeine

A simple Clojure static website generator that converts Markdown into HTML or Gemtext using themes.

## User Guide

### Requirements

- Java
- Clojure CLI

### Generate a Website

Run:

    clojure -M gen.clj

Defaults:

- Source: `content/`
- Theme: first theme alphabetically
- Intermediate files: `.meta/`
- Output: `out/`

Choose a theme and output directory with:

    clojure -M gen.clj content <theme> out

For example:

    clojure -M gen.clj content businesscasual out

### Generate Gemtext

    clojure -M gen.clj --src content --theme gemtext --out gemtext

The Gemtext theme lives in `themes/gemtext/`. Edit its
`templates/index.gmi` mold to add shared Gemtext around `{{CONTENT}}`.
The generator writes `.gmi` pages and copies assets into the output tree.

### Content

Put Markdown files in `content/`:

    content/
    ├── index.md
    ├── about.md
    └── posts/
        └── hello.md

They become:

    out/
    ├── index.html
    ├── about.html
    └── posts/
        └── hello.html

The directory structure is preserved. Markdown links to `.md` pages are automatically converted to the corresponding `.html` paths.

### Themes

Themes are stored in `themes/`:

    themes/
    ├── businesscasual/
    ├── dark/
    └── minimal/

Each theme contains its own `gen.clj` and `index.html`. Theme assets such as CSS, images, fonts, and JavaScript are automatically copied to the output.

### Workflow

1. Add Markdown files to `content/`.
2. Choose a theme in `themes/`.
3. Run `clojure -M gen.clj`.
4. Deploy the contents of `out/`.

`.meta/` is generated automatically and does not need to be created or specified manually.
