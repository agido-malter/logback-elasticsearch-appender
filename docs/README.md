# GitHub Pages documentation

The website is generated from the top-level `README.md` and `Changelog.md`.
Edit those files to update the technical documentation; do not edit generated
files in `target/pages-src/` or `target/site/`. Landing-page copy and the mapping
of README sections to pages live in `docs/build.py`; styling lives in
`docs/stylesheets/extra.css`. Navigation and website metadata are in `mkdocs.yml`.
The generator fails if README sections are added, removed or renamed without
updating the mapping.

## Local preview

Use Python 3.11 or newer. From the repository root:

```sh
python3 -m venv target/docs-venv
target/docs-venv/bin/python -m pip install -r docs/requirements.txt
target/docs-venv/bin/python docs/build.py
target/docs-venv/bin/python -m mkdocs serve
```

Open the local URL printed by MkDocs. After editing the README or changelog,
rerun `target/docs-venv/bin/python docs/build.py` to refresh the generated pages.

For the same strict build used by CI:

```sh
target/docs-venv/bin/python docs/build.py
target/docs-venv/bin/python -m mkdocs build --strict
```

## Enable publishing

After reviewing and committing the changes, set **Settings → Pages → Build and
deployment → Source** to **GitHub Actions**. Run the **Documentation** workflow
on `master`, or push a documentation change to `master`.

The workflow checks builds on pull requests to `master` and `staging` and on
pushes to both branches. Only `master` publishes, using the `github-pages`
environment. Repository settings and the repository's About website link are
not changed by these files.

Expected website: https://agido-malter.github.io/logback-elasticsearch-appender/

MkDocs generates page titles, canonical URLs and a sitemap from `mkdocs.yml`.
The README remains the technical source of truth; no Java build is needed to
build the website.
