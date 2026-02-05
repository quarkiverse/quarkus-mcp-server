#!/bin/bash
npx antora --clean antora-playbook.yml
fswatch -o modules | (while read; do npx antora antora-playbook.yml; done)
