#!/usr/bin/env python3
"""Prepare a geographically accurate, first-level administrative map of Europe.

Input: Natural Earth 1:10m Admin-1 and Admin-0 shapefiles.
Output: compact Android-friendly JSON with projected polygons and border adjacency.
"""
import argparse
import json
import math
import re
import sys
from pathlib import Path

import shapefile
from shapely.geometry import Point, box, shape, Polygon, MultiPolygon, GeometryCollection

# Europe viewport, including Iceland and western Russia.
LON_MIN, LON_MAX = -25.0, 45.0
LAT_MIN, LAT_MAX = 30.0, 73.0
MAP_LEFT, MAP_RIGHT = 20.0, 860.0
MAP_TOP, MAP_BOTTOM = 82.0, 690.0
EUROPE = box(LON_MIN, LAT_MIN, LON_MAX, LAT_MAX)

COUNTRY_NAMES = {
    "ALB":"Albanie","AND":"Andorre","AUT":"Autriche","BEL":"Belgique",
    "BGR":"Bulgarie","BIH":"Bosnie-Herzégovine","BLR":"Biélorussie",
    "CHE":"Suisse","CYP":"Chypre","CZE":"Tchéquie","DEU":"Allemagne",
    "DNK":"Danemark","ESP":"Espagne","EST":"Estonie","FIN":"Finlande",
    "FRA":"France","GBR":"Royaume-Uni","GRC":"Grèce","HRV":"Croatie",
    "HUN":"Hongrie","IRL":"Irlande","ISL":"Islande","ITA":"Italie",
    "KOS":"Kosovo","LIE":"Liechtenstein","LTU":"Lituanie","LUX":"Luxembourg",
    "LVA":"Lettonie","MCO":"Monaco","MDA":"Moldavie","MKD":"Macédoine du Nord",
    "MLT":"Malte","MNE":"Monténégro","NLD":"Pays-Bas","NOR":"Norvège",
    "POL":"Pologne","PRT":"Portugal","ROU":"Roumanie","RUS":"Russie",
    "SMR":"Saint-Marin","SRB":"Serbie","SVK":"Slovaquie","SVN":"Slovénie",
    "SWE":"Suède","TUR":"Turquie","UKR":"Ukraine","VAT":"Vatican",
    "GEO":"Géorgie","ARM":"Arménie","AZE":"Azerbaïdjan"
}
EUROPE_CODES = set(COUNTRY_NAMES)

# Approximate present-day national capitals used only for city markers and the
# initial French region selection, never for boundaries.
CAPITALS = {
    "ALB":("Tirana",19.82,41.33),"AND":("Andorre-la-Vieille",1.52,42.51),
    "AUT":("Vienne",16.37,48.21),"BEL":("Bruxelles",4.35,50.85),
    "BGR":("Sofia",23.32,42.70),"BIH":("Sarajevo",18.41,43.86),
    "BLR":("Minsk",27.56,53.90),"CHE":("Berne",7.45,46.95),
    "CYP":("Nicosie",33.38,35.18),"CZE":("Prague",14.42,50.08),
    "DEU":("Berlin",13.40,52.52),"DNK":("Copenhague",12.57,55.68),
    "ESP":("Madrid",-3.70,40.42),"EST":("Tallinn",24.75,59.44),
    "FIN":("Helsinki",24.94,60.17),"FRA":("Paris",2.35,48.86),
    "GBR":("Londres",-0.13,51.51),"GRC":("Athènes",23.73,37.98),
    "HRV":("Zagreb",15.98,45.82),"HUN":("Budapest",19.04,47.50),
    "IRL":("Dublin",-6.26,53.35),"ISL":("Reykjavik",-21.94,64.15),
    "ITA":("Rome",12.50,41.90),"KOS":("Pristina",21.17,42.66),
    "LIE":("Vaduz",9.52,47.14),"LTU":("Vilnius",25.28,54.69),
    "LUX":("Luxembourg",6.13,49.61),"LVA":("Riga",24.11,56.95),
    "MCO":("Monaco",7.42,43.74),"MDA":("Chișinău",28.86,47.01),
    "MKD":("Skopje",21.43,41.998),"MLT":("La Valette",14.51,35.90),
    "MNE":("Podgorica",19.26,42.43),"NLD":("Amsterdam",4.90,52.37),
    "NOR":("Oslo",10.75,59.91),"POL":("Varsovie",21.01,52.23),
    "PRT":("Lisbonne",-9.14,38.72),"ROU":("Bucarest",26.10,44.43),
    "RUS":("Moscou",37.62,55.75),"SMR":("Saint-Marin",12.45,43.94),
    "SRB":("Belgrade",20.46,44.82),"SVK":("Bratislava",17.11,48.15),
    "SVN":("Ljubljana",14.51,46.06),"SWE":("Stockholm",18.07,59.33),
    "TUR":("Ankara",32.86,39.93),"UKR":("Kyiv",30.52,50.45),
    "GEO":("Tbilissi",44.79,41.72),"ARM":("Erevan",44.51,40.18),
    "AZE":("Bakou",49.87,40.41)
}
# Project longitude/latitude to Mercator-like coordinates, then fit without
# changing the map aspect ratio.
def mercator_y(lat):
    lat = max(-85.0, min(85.0, lat))
    r = math.radians(lat)
    return math.log(math.tan(math.pi / 4.0 + r / 2.0))

X0, X1 = math.radians(LON_MIN), math.radians(LON_MAX)
Y0, Y1 = mercator_y(LAT_MIN), mercator_y(LAT_MAX)
SCALE = min((MAP_RIGHT - MAP_LEFT) / (X1 - X0),
            (MAP_BOTTOM - MAP_TOP) / (Y1 - Y0))
MAP_W, MAP_H = (X1 - X0) * SCALE, (Y1 - Y0) * SCALE
OFFSET_X = (MAP_LEFT + MAP_RIGHT - MAP_W) / 2.0
OFFSET_Y = (MAP_TOP + MAP_BOTTOM - MAP_H) / 2.0

def project(lon, lat):
    return [round(OFFSET_X + (math.radians(lon) - X0) * SCALE, 2),
            round(OFFSET_Y + (Y1 - mercator_y(lat)) * SCALE, 2)]

def read_shapes(shp_path):
    reader = shapefile.Reader(str(shp_path), encoding="utf-8")
    names = [f[0] for f in reader.fields[1:]]
    for item in reader.iterShapeRecords():
        props = dict(zip(names, list(item.record)))
        try:
            geom = shape(item.shape.__geo_interface__)
        except Exception:
            continue
        yield props, geom

def polygons_only(geom):
    if geom.is_empty:
        return []
    if isinstance(geom, Polygon):
        return [geom]
    if isinstance(geom, MultiPolygon):
        return list(geom.geoms)
    if isinstance(geom, GeometryCollection):
        out = []
        for sub in geom.geoms:
            out.extend(polygons_only(sub))
        return out
    return []

def slug(s):
    s = str(s or "").strip().lower()
    s = re.sub(r"[^a-z0-9]+", "-", s.encode("ascii", "ignore").decode("ascii")).strip("-")
    return s or "region"

def shp_path(shapefile_path):
    p = Path(shapefile_path)
    if p.suffix.lower() == ".shp":
        return p
    matches = list(p.glob("*.shp"))
    if not matches:
        raise FileNotFoundError(f"No .shp file in {p}")
    return matches[0]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--admin1", required=True, help="directory or path to Admin-1 shapefile")
    ap.add_argument("--admin0", required=True, help="directory or path to Admin-0 shapefile")
    ap.add_argument("--output", required=True)
    args = ap.parse_args()

    candidates = []
    seen_ids = set()
    for props, geom in read_shapes(shp_path(args.admin1)):
        code = str(props.get("adm0_a3") or props.get("ADM0_A3") or "").upper()
        if code not in EUROPE_CODES:
            continue
        clipped = geom.intersection(EUROPE)
        if clipped.is_empty or clipped.area < 0.00002:
            continue
        # Simplify with topology preserved to keep the Android asset efficient.
        clipped = clipped.simplify(0.018, preserve_topology=True)
        parts = polygons_only(clipped)
        if not parts:
            continue
        cleaned = MultiPolygon(parts) if len(parts) > 1 else parts[0]
        raw_name = props.get("name_fr") or props.get("name") or props.get("name_en") or COUNTRY_NAMES[code]
        name = str(raw_name).strip()
        raw_id = str(props.get("iso_3166_2") or props.get("adm1_code") or (code + "-" + slug(name)))
        rid = re.sub(r"[^A-Za-z0-9_-]", "", raw_id) or (code + "-" + slug(name))
        if rid in seen_ids:
            rid = rid + "-" + str(len(seen_ids))
        seen_ids.add(rid)
        rp = cleaned.representative_point()
        city, clon, clat = CAPITALS.get(code, ("", 0, 0))
        is_capital = bool(city and cleaned.distance(Point(clon, clat)) < 0.18)
        candidates.append({
            "id": rid, "name": name, "owner": COUNTRY_NAMES[code], "cc": code,
            "city": city if is_capital else "", "capital": is_capital, "geom": cleaned,
            "center": project(rp.x, rp.y)
        })

    # For very small European countries without a usable Admin-1 layer, use
    # their real national outline as a single map region rather than omit them.
    covered = {r["cc"] for r in candidates}
    for props, geom in read_shapes(shp_path(args.admin0)):
        code = str(props.get("ADM0_A3") or props.get("adm0_a3") or "").upper()
        if code not in EUROPE_CODES or code in covered:
            continue
        clipped = geom.intersection(EUROPE)
        if clipped.is_empty or clipped.area < 0.00002:
            continue
        clipped = clipped.simplify(0.012, preserve_topology=True)
        parts = polygons_only(clipped)
        if not parts:
            continue
        cleaned = MultiPolygon(parts) if len(parts) > 1 else parts[0]
        raw_name = COUNTRY_NAMES[code]
        city, clon, clat = CAPITALS.get(code, ("", 0, 0))
        is_capital = bool(city and cleaned.distance(Point(clon, clat)) < 0.18)
        candidates.append({
            "id": code + "-NATIONAL", "name": raw_name, "owner": raw_name,
            "cc": code, "city": city if is_capital else "", "capital": is_capital,
            "geom": cleaned, "center": project(*cleaned.representative_point().coords[0])
        })

    # Stable order makes saves and builds reproducible.
    candidates.sort(key=lambda r: (r["cc"], r["name"], r["id"]))
    if len(candidates) < 100:
        raise RuntimeError(f"Only {len(candidates)} regions produced; refusing to build an incomplete map.")

    # Find regions that touch along a real shared border, so adjacency follows
    # the geographic shapes rather than guessed links between map markers.
    boundaries = [r["geom"].boundary for r in candidates]
    bounds = [r["geom"].bounds for r in candidates]
    links = [set() for _ in candidates]
    for i in range(len(candidates)):
        ax0, ay0, ax1, ay1 = bounds[i]
        for j in range(i + 1, len(candidates)):
            bx0, by0, bx1, by1 = bounds[j]
            if ax1 < bx0 - 1e-8 or bx1 < ax0 - 1e-8 or ay1 < by0 - 1e-8 or by1 < ay0 - 1e-8:
                continue
            shared = boundaries[i].intersection(boundaries[j])
            if not shared.is_empty and shared.length > 0.008:
                links[i].add(j)
                links[j].add(i)

    # Nearby overseas/island regions without a land border receive one sensible
    # maritime connection to the closest other region, at most 2.5 degrees away.
    for i, r in enumerate(candidates):
        if links[i]:
            continue
        point = r["geom"].representative_point()
        best_j, best_d = None, float("inf")
        for j, other in enumerate(candidates):
            if i == j:
                continue
            d = point.distance(other["geom"].representative_point())
            if d < best_d:
                best_j, best_d = j, d
        if best_j is not None and best_d <= 2.5:
            links[i].add(best_j)
            links[best_j].add(i)

    output = []
    for i, r in enumerate(candidates):
        out_polys = []
        for poly in polygons_only(r["geom"]):
            ring = [project(x, y) for x, y in poly.exterior.coords]
            if len(ring) >= 4:
                out_polys.append(ring)
        if not out_polys:
            continue
        output.append({
            "id": r["id"], "name": r["name"], "owner": r["owner"], "cc": r["cc"],
            "city": r["city"], "capital": r["capital"],
            "center": r["center"],
            "cityPoint": project(*CAPITALS[r["cc"]][1:]) if r["capital"] else r["center"],
            "polygons": out_polys,
            "links": [candidates[j]["id"] for j in sorted(links[i])]
        })

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps({"source":"Natural Earth 1:10m Admin-1",
                                    "regions":output}, ensure_ascii=False, separators=(",", ":")),
                        encoding="utf-8")
    print(f"Prepared {len(output)} actual European administrative regions: {out_path} ({out_path.stat().st_size:,} bytes)")
    if not any(r["city"] == "Paris" for r in output):
        print("Warning: Paris region was not identified; app will select the first French region.", file=sys.stderr)

if __name__ == "__main__":
    main()
