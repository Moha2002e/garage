#!/usr/bin/env python3
"""Exécute seed.sql (ou cleanup.sql) sur la base Render, sans avoir besoin de psql.

  pip install "psycopg[binary]"
  python run_seed.py seed.sql      "<External Database URL de Render>"
  python run_seed.py cleanup.sql   "<External Database URL de Render>"
"""
import sys, psycopg
file, url = sys.argv[1], sys.argv[2]
with psycopg.connect(url, autocommit=True) as conn:
    conn.execute(open(file, encoding="utf-8").read())
    for t in ["clients","users","vehicles","appointments","quotes","quote_lines","invoices","invoice_lines"]:
        print(t, conn.execute(f"select count(*) from {t}").fetchone()[0])
