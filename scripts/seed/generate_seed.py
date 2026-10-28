#!/usr/bin/env python3
"""Génère seed.sql (données de test en masse pour la base garage) et cleanup.sql.

Usage : python3 generate_seed.py [--clients 600] [--appointments 4000] [--seed 42]
Les données de test sont repérables : e-mails en @seed.garage.test (suppression via cleanup.sql).
Mot de passe des comptes clients générés : Garage2026!
"""
import argparse, random, uuid, datetime as dt
from zoneinfo import ZoneInfo
import bcrypt

ap = argparse.ArgumentParser()
ap.add_argument("--clients", type=int, default=600)
ap.add_argument("--appointments", type=int, default=4000)
ap.add_argument("--seed", type=int, default=42)
ap.add_argument("--capacity", type=int, default=3)
args = ap.parse_args()
rnd = random.Random(args.seed)
PARIS = ZoneInfo("Europe/Paris")
NOW = dt.datetime.now(dt.timezone.utc)
DOMAIN = "seed.garage.test"
PASSWORD = "Garage2026!"

FIRST = ["Lucas","Emma","Louis","Léa","Hugo","Chloé","Jules","Manon","Adam","Inès","Nathan","Camille","Gabriel","Sarah","Arthur","Lina","Théo","Jade","Ethan","Zoé","Noah","Alice","Liam","Louise","Mehdi","Yasmine","Karim","Sofia","Pierre","Marie","Thomas","Julie","Antoine","Laura","Nicolas","Sophie","Maxime","Clara","Romain","Anaïs"]
LAST = ["Martin","Bernard","Dubois","Thomas","Robert","Richard","Petit","Durand","Leroy","Moreau","Simon","Laurent","Lefebvre","Michel","Garcia","David","Bertrand","Roux","Vincent","Fournier","Morel","Girard","André","Mercier","Dupont","Lambert","Bonnet","François","Martinez","Legrand","Garnier","Faure","Rousseau","Blanc","Guerin","Muller","Henry","Roussel","Nicolas","Perrin","El Amrani","Benali","Peeters","Janssens","Claes","Maes"]
CARS = {"Renault":["Clio","Megane","Captur","Twingo","Scenic"],"Peugeot":["208","308","2008","3008","508"],"Citroën":["C3","C4","Berlingo","C5 Aircross"],"Volkswagen":["Golf","Polo","Tiguan","Passat"],"Toyota":["Yaris","Corolla","Aygo","RAV4"],"Ford":["Fiesta","Focus","Kuga"],"BMW":["Série 1","Série 3","X1"],"Audi":["A3","A4","Q3"],"Mercedes":["Classe A","Classe C","GLA"],"Dacia":["Sandero","Duster","Logan"],"Opel":["Corsa","Astra","Mokka"],"Fiat":["500","Panda","Tipo"],"Hyundai":["i20","Tucson"],"Kia":["Picanto","Sportage"],"Tesla":["Model 3","Model Y"]}
# (motif du RDV, durée en minutes, lignes de devis : (description, quantité, prix unitaire min, max))
JOBS = [
 ("Vidange et remplacement du filtre à huile",60,[("Huile moteur 5W30 (litre)",(4,5),(9,14)),("Filtre à huile",(1,1),(8,20)),("Main d'œuvre",(1,1.5),(45,60))]),
 ("Remplacement des plaquettes de frein avant",90,[("Plaquettes de frein avant (jeu)",(1,1),(35,90)),("Main d'œuvre",(1,2),(45,60))]),
 ("Remplacement des disques et plaquettes",150,[("Disques de frein (paire)",(1,1),(80,180)),("Plaquettes de frein (jeu)",(1,1),(35,90)),("Main d'œuvre",(2,3),(45,60))]),
 ("Contrôle technique préparatoire",45,[("Diagnostic complet",(1,1),(35,60)),("Main d'œuvre",(0.5,1),(45,60))]),
 ("Changement des pneus (x4)",75,[("Pneu (unité)",(4,4),(55,140)),("Équilibrage",(4,4),(8,12)),("Montage",(1,1),(30,50))]),
 ("Révision complète constructeur",180,[("Kit révision",(1,1),(120,260)),("Huile moteur (litre)",(4,5),(9,14)),("Main d'œuvre",(2,3.5),(45,60))]),
 ("Remplacement de la batterie",30,[("Batterie 12V",(1,1),(90,220)),("Main d'œuvre",(0.5,1),(45,60))]),
 ("Diagnostic voyant moteur",60,[("Diagnostic électronique",(1,1),(40,70)),("Main d'œuvre",(0.5,1.5),(45,60))]),
 ("Remplacement de la courroie de distribution",240,[("Kit de distribution",(1,1),(150,380)),("Pompe à eau",(1,1),(40,110)),("Main d'œuvre",(3,4),(45,60))]),
 ("Recharge de climatisation",45,[("Gaz R134a / R1234yf",(1,1),(45,110)),("Main d'œuvre",(0.5,1),(45,60))]),
 ("Remplacement des balais d'essuie-glace",15,[("Balais d'essuie-glace (paire)",(1,1),(12,35))]),
 ("Géométrie et parallélisme",60,[("Réglage géométrie",(1,1),(60,100)),("Main d'œuvre",(0.5,1),(45,60))]),
 ("Remplacement de l'embrayage",300,[("Kit embrayage",(1,1),(180,450)),("Main d'œuvre",(4,5),(45,60))]),
 ("Remplacement de l'échappement",120,[("Silencieux",(1,1),(70,220)),("Main d'œuvre",(1.5,2),(45,60))]),
]

def new_id(): return str(uuid.UUID(int=rnd.getrandbits(128), version=4))
def q(s): return "'" + str(s).replace("'", "''") + "'"
def ts(d): return q(d.astimezone(dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%S+00"))
def money(x): return f"{x:.2f}"
def round2(x): return (x * 100 + 0.5) // 1 / 100

pw_hash = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=10, prefix=b"2a")).decode()
plates = set()
def plate():
    while True:
        p = f"{rnd.choice('ABCDEFGHJKLMNPQRSTVWXYZ')}{rnd.choice('ABCDEFGHJKLMNPQRSTVWXYZ')}-{rnd.randint(100,999)}-{rnd.choice('ABCDEFGHJKLMNPQRSTVWXYZ')}{rnd.choice('ABCDEFGHJKLMNPQRSTVWXYZ')}"
        if p not in plates:
            plates.add(p); return p

def strip(s):
    import unicodedata
    return "".join(c for c in unicodedata.normalize("NFD", s.lower()) if unicodedata.category(c) != "Mn").replace(" ", "")

clients, users, vehicles = [], [], []
for i in range(args.clients):
    cid = new_id(); fn, ln = rnd.choice(FIRST), rnd.choice(LAST)
    created = NOW - dt.timedelta(days=rnd.randint(30, 700))
    email = f"{strip(fn)}.{strip(ln)}{i}@{DOMAIN}"
    phone = "+32 4" + str(rnd.randint(70,99)) + " " + f"{rnd.randint(10,99)} {rnd.randint(10,99)} {rnd.randint(10,99)}"
    clients.append((cid, ln, fn, email, phone, rnd.random() > 0.03, created))
    if rnd.random() < 0.35:
        users.append((new_id(), email, fn, ln, rnd.choice(["FR","FR","FR","EN","NL"]), cid, created))
    for _ in range(rnd.choices([1,2,3],[65,28,7])[0]):
        b = rnd.choice(list(CARS)); vehicles.append((new_id(), cid, plate(), b, rnd.choice(CARS[b]), created))

# --- Planning : capacité globale <= N, pas de chevauchement sur un même véhicule ---
slots = []  # (start, end, vehicle)
def free(start, end, vid):
    conc = 0
    for s, e, v in slots:
        if s < end and e > start:
            if v == vid: return False
            conc += 1
    return conc < args.capacity

by_day = {}
def day_ok(start, end, vid):
    key = start.astimezone(PARIS).date()
    return key, all(True for _ in ()) and free_day(by_day.get(key, []), start, end, vid)
def free_day(lst, start, end, vid):
    conc = 0
    for s, e, v in lst:
        if s < end and e > start:
            if v == vid: return False
            conc += 1
    return conc < args.capacity

vlist = [(v[0], v[1]) for v in vehicles]
appts = []
attempts = 0
while len(appts) < args.appointments and attempts < args.appointments * 40:
    attempts += 1
    vid, cid = rnd.choice(vlist)
    job = rnd.choice(JOBS); dur = job[1]
    offset = int(rnd.triangular(-365, 75, -20))
    day = (NOW.astimezone(PARIS) + dt.timedelta(days=offset)).date()
    if day.weekday() == 6: continue
    hour = rnd.randint(8, 16); minute = rnd.choice([0,15,30,45])
    start = dt.datetime(day.year, day.month, day.day, hour, minute, tzinfo=PARIS)
    end = start + dt.timedelta(minutes=dur)
    if end.astimezone(PARIS).hour >= 18 and end.astimezone(PARIS).minute > 0: continue
    if day == NOW.astimezone(PARIS).date() and rnd.random() < 0.5: pass
    lst = by_day.setdefault(day, [])
    if not free_day(lst, start, end, vid): continue
    lst.append((start, end, vid))
    appts.append((new_id(), cid, vid, start, dur, job))

appt_rows, quote_rows, line_rows, inv_rows, invline_rows = [], [], [], [], []
inv_seq = 0
nb_invoiced = nb_pending = 0
for aid, cid, vid, start, dur, job in sorted(appts, key=lambda a: a[3]):
    end = start + dt.timedelta(minutes=dur)
    if end < NOW:
        status = rnd.choices(["COMPLETED", "CANCELLED"], [85, 15])[0]
    elif start <= NOW < end:
        status = "IN_PROGRESS"
    else:
        status = "PLANNED"
    created = start - dt.timedelta(days=rnd.randint(1, 20), hours=rnd.randint(0, 8))
    appt_rows.append((aid, cid, vid, start, job[0], status, dur, created))
    if status == "CANCELLED" and rnd.random() < 0.8: continue
    if status == "PLANNED" and rnd.random() < 0.4: continue
    qid = new_id()
    if status == "COMPLETED": qs = rnd.choices(["ACCEPTED","REFUSED","DRAFT"], [88, 4, 8])[0]
    elif status == "IN_PROGRESS": qs = "ACCEPTED"
    else: qs = rnd.choices(["DRAFT","ACCEPTED"], [60, 40])[0]
    lines, total = [], 0.0
    for n, (desc, qr, pr) in enumerate(job[2]):
        qty = round(rnd.uniform(*qr) * 2) / 2 if qr[0] != qr[1] else qr[0]
        price = round(rnd.uniform(*pr), 2)
        lt = round2(qty * price); total += lt
        lines.append((new_id(), qid, n + 1, desc, qty, price, created))
    total = round(total, 2)
    quote_rows.append((qid, aid, qs, total, created)); line_rows += lines
    if status == "COMPLETED" and qs == "ACCEPTED":
        if rnd.random() < 0.6:   # déjà facturé ; le reste est laissé au job Spring Batch
            inv_seq += 1; nb_invoiced += 1
            issued = end + dt.timedelta(hours=rnd.randint(1, 48))
            iid = new_id(); ist = "PAID" if rnd.random() < 0.7 and issued < NOW else "ISSUED"
            paid = issued + dt.timedelta(days=rnd.randint(0, 30)) if ist == "PAID" else None
            if paid and paid > NOW: paid = NOW - dt.timedelta(hours=1)
            number = f"FAC-{issued.astimezone(PARIS).year:04d}-{inv_seq:06d}"
            inv_rows.append((iid, number, aid, cid, total, ist, issued, paid))
            for l in lines:
                invline_rows.append((new_id(), iid, l[2], l[3], l[4], l[5], round2(l[4] * l[5]), issued))
        else:
            nb_pending += 1

# --- Écriture SQL ---
def insert(f, table, cols, rows, fmt, chunk=500):
    for i in range(0, len(rows), chunk):
        f.write(f"INSERT INTO {table} ({', '.join(cols)}) VALUES\n")
        f.write(",\n".join("(" + fmt(r) + ")" for r in rows[i:i+chunk]) + ";\n")

with open("seed.sql", "w", encoding="utf-8") as f:
    f.write("-- Données de test générées par generate_seed.py (e-mails @%s)\nBEGIN;\n" % DOMAIN)
    insert(f, "clients", ["id","last_name","first_name","email","phone","active","created_at","updated_at"], clients,
           lambda r: f"'{r[0]}',{q(r[1])},{q(r[2])},{q(r[3])},{q(r[4])},{'true' if r[5] else 'false'},{ts(r[6])},{ts(r[6])}")
    insert(f, "users", ["id","email","password_hash","first_name","last_name","role","language","enabled","client_id","created_at","updated_at"], users,
           lambda r: f"'{r[0]}',{q(r[1])},{q(pw_hash)},{q(r[2])},{q(r[3])},'CLIENT',{q(r[4])},true,'{r[5]}',{ts(r[6])},{ts(r[6])}")
    insert(f, "vehicles", ["id","client_id","plate","brand","model","active","created_at","updated_at"], vehicles,
           lambda r: f"'{r[0]}','{r[1]}',{q(r[2])},{q(r[3])},{q(r[4])},true,{ts(r[5])},{ts(r[5])}")
    insert(f, "appointments", ["id","client_id","vehicle_id","appointment_date","reason","status","duration_minutes","created_at","updated_at"], appt_rows,
           lambda r: f"'{r[0]}','{r[1]}','{r[2]}',{ts(r[3])},{q(r[4])},'{r[5]}',{r[6]},{ts(r[7])},{ts(r[7])}")
    insert(f, "quotes", ["id","appointment_id","status","total","created_at","updated_at"], quote_rows,
           lambda r: f"'{r[0]}','{r[1]}','{r[2]}',{money(r[3])},{ts(r[4])},{ts(r[4])}")
    insert(f, "quote_lines", ["id","quote_id","sort_order","description","quantity","unit_price","active","created_at","updated_at"], line_rows,
           lambda r: f"'{r[0]}','{r[1]}',{r[2]},{q(r[3])},{money(r[4])},{money(r[5])},true,{ts(r[6])},{ts(r[6])}")
    insert(f, "invoices", ["id","number","appointment_id","client_id","total","status","issued_at","paid_at","created_at","updated_at"], inv_rows,
           lambda r: f"'{r[0]}',{q(r[1])},'{r[2]}','{r[3]}',{money(r[4])},'{r[5]}',{ts(r[6])},{ts(r[7]) if r[7] else 'NULL'},{ts(r[6])},{ts(r[6])}")
    insert(f, "invoice_lines", ["id","invoice_id","sort_order","description","quantity","unit_price","total","created_at","updated_at"], invline_rows,
           lambda r: f"'{r[0]}','{r[1]}',{r[2]},{q(r[3])},{money(r[4])},{money(r[5])},{money(r[6])},{ts(r[7])},{ts(r[7])}")
    # La séquence doit passer après les numéros insérés, sinon le job Batch produirait des doublons.
    f.write(f"SELECT setval('invoice_number_seq', GREATEST((SELECT last_value FROM invoice_number_seq), {max(inv_seq,1)}));\nCOMMIT;\n")

with open("cleanup.sql", "w") as f:
    f.write(f"""-- Supprime uniquement les données de test (e-mails @{DOMAIN}) et ce qui en dépend.
BEGIN;
DELETE FROM invoice_lines WHERE invoice_id IN (SELECT i.id FROM invoices i JOIN clients c ON c.id=i.client_id WHERE c.email LIKE '%@{DOMAIN}');
DELETE FROM invoices WHERE client_id IN (SELECT id FROM clients WHERE email LIKE '%@{DOMAIN}');
DELETE FROM quote_lines WHERE quote_id IN (SELECT q.id FROM quotes q JOIN appointments a ON a.id=q.appointment_id JOIN clients c ON c.id=a.client_id WHERE c.email LIKE '%@{DOMAIN}');
DELETE FROM quotes WHERE appointment_id IN (SELECT a.id FROM appointments a JOIN clients c ON c.id=a.client_id WHERE c.email LIKE '%@{DOMAIN}');
DELETE FROM appointments WHERE client_id IN (SELECT id FROM clients WHERE email LIKE '%@{DOMAIN}');
DELETE FROM vehicles WHERE client_id IN (SELECT id FROM clients WHERE email LIKE '%@{DOMAIN}');
DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@{DOMAIN}');
DELETE FROM users WHERE email LIKE '%@{DOMAIN}';
DELETE FROM clients WHERE email LIKE '%@{DOMAIN}';
COMMIT;
""")

print(f"clients={len(clients)} comptes={len(users)} véhicules={len(vehicles)} rdv={len(appt_rows)} (demandés {args.appointments}) "
      f"devis={len(quote_rows)} lignes={len(line_rows)} factures={len(inv_rows)} (+{nb_pending} à facturer par le batch)")
