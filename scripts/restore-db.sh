#!/usr/bin/env bash
# ==========================================================================
#  Mini Bank — PHASE 10B : PostgreSQL RESTORE scripti
# ==========================================================================
#  МАКСАТ
#  Backup (.dump) файлынан маалымат базасын КАЙТА ОКУУ (recovery).
#
#  ⚠️ БУЛ ЖОК ЭТУҮЧҮ БУЙРУК!
#  pg_restore «--clean» режимисинде АЛДЫНДА бар болгон объектти
#  ОЧҮРӨТ (DROP TABLE users, DROP TABLE accounts, ...). Демек бул
#  азаптан көрсөткөн таблицалардын БАРЫ жоголот. «Кайра келтирүү»
#  деген сөз бул жерде «орнитту» дегенди билдирет.
#
#  🛡️ КООПСУЗДУК (PHASE 10B §7)
#  Биз «ишке кирүү» үчүн 3 катмар коёт:
#    1. ЭЧ КАЧАН автоматтык түрдө өчүрүп кетпейт: `--yes` берилбэсе
#       ИШЛЕМЕЙТ (dry-run жасап, көрсөтүп, токтотуп).
#    2. Иштей баштоодон МУРУН ачык WARNING чыгарат: кайсы база,
#       канча таблица жоголору көрсөтүлөт.
#    3. «--yes» берилсе гана өтүшкөн таблица санын СУРАП, «yes»
#       деген жазууну гана кабылдайт (хыжарат `--yes-please`).
#
#  КАЧЫНЧА УЛУШУ:
#     ./scripts/restore-db.sh backups/minibank_20260101_120000.dump
#     ./scripts/restore-db.sh backups/minibank_20260101_120000.dump --yes-please
#
#  КАЙТЫШ:
#     0 — ийгилик
#     1 — жатуу/файл/папка катасы
#     2 — Docker/контейнер/улашуу катасы
#     3 — pg_restore катасы
#     4 — колдонуучу баш ташту (жок тастарында)
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_DIR}"

log()  { printf '[restore] %s\n' "$*"; }
warn() { printf '[restore] ⚠️  %s\n' "$*" >&2; }
fail() { printf '[restore] ХАТА: %s\n' "$*" >&2; }

# ==========================================================================
#  1) АРГУМЕНТТЕР
# ==========================================================================
BACKUP_FILE=""
ASSUME_YES="false"

for arg in "$@"; do
    case "${arg}" in
        --yes-please)
            ASSUME_YES="true"
            ;;
        -h|--help)
            log "Колдонуу: $0 <backup.dump> [--yes-please]"
            log ""
            log "  <backup.dump>   — backup-db.sh менен жасалган файл"
            log "  --yes-please    — «ооба, маалыматы өчүр» деп ыктымал"
            log "                   БУЛСАЗ скрипт ЭЧ НИМЕ ӨЧҮРБЕЙТ,"
            log "                   ал гана көрсөтүп токтотуп чыгат."
            exit 0
            ;;
        -*)
            fail "Билбейсиң аргумент: ${arg}  (--help кара)"
            exit 1
            ;;
        *)
            if [[ -n "${BACKUP_FILE}" ]]; then
                fail "Эки файл берилди: «${BACKUP_FILE}» жана «${arg}»."
                exit 1
            fi
            BACKUP_FILE="${arg}"
            ;;
    esac
done

if [[ -z "${BACKUP_FILE}" ]]; then
    fail "Backup файлы көрсөтүлгөн жок."
    fail "Колдонуу: $0 <backup.dump> [--yes-please]"
    exit 1
fi

# ==========================================================================
#  2) ФАЙЛ ТЕКШЕРҮҮСҮ  (ЭЧ КАЧАН АЛДЫНДА!)
# ==========================================================================
#  🔴 МААНИЛҮҮ ТАРТИП: файл текшерүүсү МЫС ӨЧҮРҮҮДӨН АЛДЫН жүрөт.
#  Болбосон, «файл жок» деген катаны чыгаруу үчүн бизге DB'ни
#  ӨЧҮРҮП ЖІБЕРҢИМИЗ — эң купдук «караталашыны» кылыш болмак эле.
# ==========================================================================
if [[ ! -e "${BACKUP_FILE}" ]]; then
    fail "Backup файлы ТАБЫЛМАДИ: ${BACKUP_FILE}"
    exit 1
fi

if [[ -d "${BACKUP_FILE}" ]]; then
    fail "Бу ФАЙЛ ЭМЕС, папка: ${BACKUP_FILE}"
    exit 1
fi

if [[ ! -r "${BACKUP_FILE}" ]]; then
    fail "Файлды ОКУУ МҮМКҮН ЭМЕС: ${BACKUP_FILE}"
    exit 1
fi

if [[ ! -s "${BACKUP_FILE}" ]]; then
    fail "Backup файлы БОШ (0 байт) — калгана эч нерсе жок."
    exit 1
fi

BACKUP_FILE="$(cd -- "$(dirname -- "${BACKUP_FILE}")" && pwd)/$(basename -- "${BACKUP_FILE}")"
BACKUP_SIZE="$(wc -c < "${BACKUP_FILE}" | tr -d ' ')"

# ==========================================================================
#  3) .env ОКУУ
# ==========================================================================
ENV_FILE="${PROJECT_DIR}/.env"
if [[ ! -f "${ENV_FILE}" ]]; then
    fail ".env табылмады: ${ENV_FILE}"
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

DB_NAME="${POSTGRES_DB:-}"
DB_USER="${POSTGRES_USER:-}"

if [[ -z "${DB_NAME}" || -z "${DB_USER}" ]]; then
    fail ".env ичинде POSTGRES_DB жана POSTGRES_USER КЕРЕК."
    exit 1
fi

# ==========================================================================
#  4) DOCKER / КОНТЕЙНЕР
# ==========================================================================
if ! command -v docker >/dev/null 2>&1; then
    fail "docker табылмады."
    exit 2
fi

if ! docker info >/dev/null 2>&1; then
    fail "Docker иштемийт."
    exit 2
fi

CONTAINER_ID="$(docker compose ps -q postgres 2>/dev/null || true)"
if [[ -z "${CONTAINER_ID}" ]]; then
    fail "«postgres» кызматы иштеп жаткан контейнер табылмады."
    fail "Адегенде:  docker compose up -d"
    exit 2
fi

RUNNING="$(docker inspect -f '{{.State.Running}}' "${CONTAINER_ID}" 2>/dev/null || echo false)"
if [[ "${RUNNING}" != "true" ]]; then
    fail "PostgreSQL контейнери иштемийт."
    exit 2
fi

if ! docker exec "${CONTAINER_ID}" psql -U "${DB_USER}" -d "${DB_NAME}" -c 'SELECT 1' >/dev/null 2>&1; then
    fail "Маалымат базасына кошулуш мүмкүн болду: ${DB_NAME}"
    exit 2
fi

psql_q() {
    docker exec "${CONTAINER_ID}" psql -U "${DB_USER}" -d "${DB_NAME}" -tAc "$1" 2>/dev/null
}

# ==========================================================================
#  5) ФАЙЛДЫ ТЕКШЕРҮҮ (pg_restore --list)  — БУЗУЛГАНДЫ АЧАБЫЗ?
# ==========================================================================
#  🧡 ЭМНЕ БУЛ КЕРЕК?
#  Үзүнкү «backup» файлы азыр «калга» кийин бузулган болсо,
#  pg_restore орто жерде токтоп, базаны ЖАРЫМ ЭМЕС абалда калтырап
#  берет — эң жаман кечигим (маалымат «жарты»).
#  Ошондуктан биз АЛДЫНДА текшеребиз: `pg_restore --list` — бул
#  файлды ОКУП гана, эч нерсе ӨЗГӨРТҮБЕЙТ.
# ==========================================================================
log "Backup файлын текшерүү (pg_restore --list)..."

# custom формат ОКУУ ҮЧҮН «кайта издөө» (seek) керек → stdin эмес,
# контейнерге көчүрүп, анда текшеребиз, дагы өчүрөбүз.
TMP_IN_CONTAINER="/tmp/verify_$$.dump"
if ! docker cp "${BACKUP_FILE}" "${CONTAINER_ID}:${TMP_IN_CONTAINER}" >/dev/null 2>&1; then
    fail "Backup файлын контейнерге көчүрүү МҮМКҮН БОЛДУ."
    exit 1
fi

LISTING="$(docker exec "${CONTAINER_ID}" pg_restore --list "${TMP_IN_CONTAINER}" 2>&1)" && LIST_RC=0 || LIST_RC=$?
docker exec "${CONTAINER_ID}" rm -f "${TMP_IN_CONTAINER}" >/dev/null 2>&1 || true

if [[ ${LIST_RC} -ne 0 ]]; then
    fail "Backup файлы БУЗУЛГАН (pg_restore --list катасы):"
    printf '%s\n' "${LISTING}" >&2
    exit 1
fi

# Негизги объекттер барбы? (PHASE 10B §10)
#
# 🔴 ТАРТИП МААНИЛҮҮ — pg_restore --list КАЙСЫ ЖОЛ ЧЫГАРАРЫ:
#
#     217; 1259 16385 TABLE public users              minibank
#     3458; 0  16385 TABLE DATA public users          minibank
#
#   • Ат СИРТТА «minibank» (ээси) — ОШОНДУКТАН «...users$»
#     деп текшерсек, АСТАСЫНАН ЭЧ УЧРАН ыйнамак эле (бизде
#     «tables MISSING» деп чалышты -> чыныгы толук backup'ты
#     «бузулган» деп карал эле).
#   • Биз «TABLE» ЖЭНЕ «TABLE DATA» экөөсүн үлгөртөт: бир
#     таблицанын СКЕМАСЫ да, МААЛЫМАТЫ да болушу керек.
MISSING=""
for obj in "users" "accounts" "transactions" "flyway_schema_history"; do
    if ! printf '%s\n' "${LISTING}" | grep -qE "TABLE( DATA)? public ${obj}[[:space:]]"; then
        MISSING="${MISSING} ${obj}"
    fi
done

if [[ -n "${MISSING}" ]]; then
    fail "Backup ичинде күткөн таблицалар ЖОК:${MISSING}"
    fail "Бул backup толук эмес — калгана келтирүү МЫМКҮН ЭМЕС."
    exit 1
fi

log "Текшерүү өттү: users, accounts, transactions, flyway_schema_history баары бар."

# ==========================================================================
#  6) ⚠️  ЖОК ЭТҮҮЧҮ КЕЗЕК  (PHASE 10B §7)
# ==========================================================================
CURRENT_COUNTS="$(psql_q "select coalesce((select count(*) from users),0)||'/'||coalesce((select count(*) from accounts),0)||'/'||coalesce((select count(*) from transactions),0);" || echo '?/?/?')"
C_NAME="$(docker inspect -f '{{.Name}}' "${CONTAINER_ID}" | sed 's|^/||')"

cat >&2 <<WARN
╔══════════════════════════════════════════════════════════════════════╗
║  ⚠️  БУЛ ЖОК ЭТҮҮЧҮ БУЙРУК!                                      ║
╚══════════════════════════════════════════════════════════════════════╝
  База         : ${DB_NAME}
  Контейнер    : ${C_NAME}
  Backup файлы : ${BACKUP_FILE} (${BACKUP_SIZE} байт)

  Азыркы маалымат (users/accounts/transactions): ${CURRENT_COUNTS}

  Келе эле БУТ таблицалар ӨЧҮРИЛӨТ жана backup'тан калгана келтирилет:
      users, accounts, transactions, flyway_schema_history
      + эсеп номуру sequence (account_number_seq)
      + бардык CHECK / UNIQUE / FOREIGN KEY чекемелер

  Эгер СИЗ ушул базада иштеп жаткан болсоңуз — ал маалымат
  УЧУП КЕТЕД жана аны КАЙТАРЫЛБАЙТ.
WARN

if [[ "${ASSUME_YES}" != "true" ]]; then
    cat >&2 <<'DRY'
──────────────────────────────────────────────────────────────────────
  БУЛ СЫНОО ЖОК (dry-run). ЭЧ НИМЕ ӨЗГӨРТҮЛМӮДҮ.
  Эгер чыныгыдан калгана келтиргиңиз келсе:

      $0 "${BACKUP_FILE}" --yes-please

  🧡 БУЛ ЖЕРДЕ ПРОЕКТ — ЖЕРГИЛИКТИ ОКУУ ДОЛБООР. Чыныгы
     өндүрүш базасына бул буйрукты БУЙУРМАЙЫН.
──────────────────────────────────────────────────────────────────────
DRY
    exit 0
fi

# «--yes-please» берилген — бирок интерактивдүү болсо, АЛДАЖЫ
# сурабыз. (Скрипт автоматтык CI'да иштесе stdin түкүн болот —
#  ал ошондо суроо өткөрбөйт.)
if [[ -t 0 ]]; then
    printf '\n  БАЗАНЫ ОЧҮРУУГА ИШЕНЧҮҋҮНҮЗБЕ? ҮҮН ҮЧҮН «yes» деп жазыңыз: '
    read -r ANSWER
    if [[ "${ANSWER}" != "yes" ]]; then
        fail "Баш таштырылды («${ANSWER}» ≠ «yes»). Эч нерсе өзгөртүлмөдү."
        exit 4
    fi
fi

log "Жок этүү башталып жатат..."

# ==========================================================================
#  7) ЭСИРГЕ КАЛГА КЕЛТИРҮҮ
# ==========================================================================
#  🧡 pg_restore --clean --if-exists :
#     • «--clean»  — баштапкы аныктамалардан КИЙИН DROP TABLE /
#       DROP SEQUENCE иштетет. Болбосон «already exists» катасы.
#     • «--if-exists» — эгер объект жок болсо, DROP ТИЗИМДИ ТАШТАБАЙТ.
#       Бул маанилүү: эгер backup базада жок болгон таблицаны
#       калгана келтирсе, биздин бутун катта иш өтпөй калмак эле.
#     • «--exit-on-error» — биринчи катада токтотуп, бизге
#       «жарты калгана келди» деген абалды КӨРСӨТҮБЕЙТ.
# ==========================================================================
if docker cp "${BACKUP_FILE}" "${CONTAINER_ID}:${TMP_IN_CONTAINER}" >/dev/null 2>&1; then
    if docker exec "${CONTAINER_ID}" \
            pg_restore -U "${DB_USER}" -d "${DB_NAME}" \
            --clean --if-exists --no-owner --no-privileges \
            --exit-on-error \
            "${TMP_IN_CONTAINER}"; then
        RESTORE_OK="true"
    else
        RESTORE_OK="false"
    fi
    docker exec "${CONTAINER_ID}" rm -f "${TMP_IN_CONTAINER}" >/dev/null 2>&1 || true
else
    fail "Backup файлын контейнерге көчүрүү МҮМКҮН БОЛДУ."
    exit 2
fi

if [[ "${RESTORE_OK}" != "true" ]]; then
    fail "pg_restore КАТАСЫ. База ТОЛУК эмес абалда болушу мүмкүн."
    exit 3
fi

# ==========================================================================
#  8) КАЛГАНДАН КЕЙИН ТЕКШЕРҮҮ
# ==========================================================================
log "Калгана келген таблицаларды текшерүү..."
RESTORED_COUNTS="$(psql_q "select coalesce((select count(*) from users),0)||'/'||coalesce((select count(*) from accounts),0)||'/'||coalesce((select count(*) from transactions),0);" || echo '?/?/?')"
FLYWAY_ROWS="$(psql_q "select count(*) from flyway_schema_history;" || echo '0')"
SEQUENCE_FOUND="$(psql_q "select count(*) from information_schema.sequences where sequence_name='account_number_seq';" || echo '0')"

if [[ "${FLYWAY_ROWS}" == "0" || "${SEQUENCE_FOUND}" == "0" ]]; then
    fail "Калгана келген база ТОЛУК ЭМЕС (Flyway же sequence жок)."
    exit 3
fi

log "ЫЙГИЛИК: база backup'тан калгана келтирилди."
log "users/accounts/transactions = ${RESTORED_COUNTS}"
log "flyway_schema_history = ${FLYWAY_ROWS} жазуу, sequence бар"