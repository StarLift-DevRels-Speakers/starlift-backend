# Локальный разбор русской речи

Анализатор обрабатывает аудио одного основного спикера и выдаёт JSON-отчёт.
Распознавание выполняется через whisper.cpp на Linux CPU или на Mac с Metal, а паузы измеряются
отдельным проходом Silero VAD по исходному звуку. Для структуры речи и
контекстных примеров можно подключить Qwen3 через локальный llama-server.

## Сервер Linux: production Compose

`docker-compose.yml` включает `audio-worker`: Python, FFmpeg, whisper.cpp
v1.9.1 и Silero VAD. GPU и macOS не нужны. На x86 образ собирает варианты
CPU-бэкенда с выбором подходящего при запуске, без `-march=native`.

При деплое из `develop` workflow сохраняет общий случайный
`AUDIO_ANALYSIS_WORKER_TOKEN` в `~/starlift/env/backend.env` и передаёт его
обоим сервисам. Непустой существующий ключ не меняется. Ручной запуск после
настройки `.env`:

```sh
python3 tools/audio-analysis/ensure_worker_token.py .env
docker compose up -d --build --wait --wait-timeout 1800
```

Первый запуск скачивает модели в том `audio_models`; последующие используют
кэш. SHA-256 проверяется перед запуском. По умолчанию используется `turbo`
(large-v3-turbo Q5). Для менее мощного CPU в `.env` можно выбрать
`AUDIO_ANALYSIS_MODEL=small`; качество распознавания будет отличаться.
Лимиты контейнера по умолчанию: 2 CPU и 2 ГиБ. Они задаются через
`AUDIO_ANALYSIS_CPUS` и `AUDIO_ANALYSIS_MEMORY`. Существующий таймаут анализа
12 минут остаётся в силе: скорость длинных записей нужно измерить на сервере.

Воркер обращается к `http://app:8080` с явным `--allow-insecure-http` только
внутри Docker-сети. По умолчанию CLI по-прежнему принимает HTTP только на
loopback. Публиковать `/internal/audio-analyses` в nginx не требуется.
`APP_AUDIO_INTERNAL_DOWNLOAD=true` у app включает подписанные ссылки на
внутренний адрес MinIO (`S3_ENDPOINT=http://minio:9000`). Нативный запуск
на Mac сохраняет прежнее поведение, значение по умолчанию — `false`.

Ссылки для браузера используют `S3_PUBLIC_ENDPOINT`, по умолчанию
`https://startlift.duckdns.org`. Nginx проксирует GET/HEAD `/lectures-media/`
в MinIO с исходным Host для проверки SigV4. URL нельзя переписывать после
подписания. Если в серверном `.env` остался `S3_PUBLIC_ENDPOINT=http://localhost:9000`,
замените его внешним HTTPS-адресом. MinIO отклоняет запросы без корректной подписи.

Healthcheck воркера становится успешным после загрузки моделей и первого
успешного авторизованного запроса к API. Он допускает обработку одной записи
до 12 минут и становится неуспешным после 14 минут без подтверждённой связи.
Workflow ждёт готовности сервисов до 30 минут, включая первую загрузку моделей.

```sh
docker compose ps
docker compose logs --tail=100 audio-worker app
docker stats --no-stream
```

После развёртывания загрузите короткую русскую запись через «Медиа» и
проверьте `QUEUED → RUNNING → SUCCEEDED`, открытие отчёта и воспроизведение.
Уже созданные задачи в очереди будут подобраны автоматически. Код и локальные
контейнерные проверки не заменяют этот прогон на фактическом сервере.

## Подготовка

Нужны Python 3, FFmpeg, `whisper-cli` и `whisper-vad-speech-segments` в `PATH`.
На macOS с поддержкой Homebrew подойдёт `brew install ffmpeg whisper.cpp`.
Для сборки на Apple Silicon нужны CMake и инструменты командной строки Xcode.

```sh
audio_work="$HOME/.local/share/speakers-audio-analysis"
mkdir -p "$audio_work"
git clone --depth 1 --branch v1.9.1 https://github.com/ggml-org/whisper.cpp.git "$audio_work/whisper"
cmake -S "$audio_work/whisper" -B "$audio_work/whisper/build" \
  -DCMAKE_BUILD_TYPE=Release -DGGML_METAL_EMBED_LIBRARY=ON
cmake --build "$audio_work/whisper/build" -j 6 --target whisper-cli whisper-vad-speech-segments
export PATH="$audio_work/whisper/build/bin:$PATH"

curl -fL -o "$audio_work/ggml-large-v3-turbo-q5_0.bin" \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin
curl -fL -o "$audio_work/ggml-silero-v6.2.0.bin" \
  https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v6.2.0.bin
```

## Запуск

Из корня репозитория.

```sh
python3 tools/audio-analysis/analyze.py recording.m4a \
  --model "$audio_work/ggml-large-v3-turbo-q5_0.bin" \
  --vad-model "$audio_work/ggml-silero-v6.2.0.bin" > report.json
```

Отчёт содержит русский транскрипт с границами сегментов, темп в словах в
минуту, интервалы пауз от 700 мс и кандидатов в слова-паразиты, повторы
и слышимые заминки. Порог пауз задаётся через `--min-pause-ms`. Темп
рассчитывается между первой и последней обнаруженной репликой с учётом
внутренних пауз. Времена даны в миллисекундах от начала файла. Кандидаты
ссылаются на сегменты Whisper. По контексту кандидат может оказаться
обычным словом или намеренным повтором.

Для разбора структуры скачайте [llama.cpp для macOS arm64](https://github.com/ggml-org/llama.cpp/releases/tag/b12090)
и [Qwen3-4B Q4_K_M](https://huggingface.co/Qwen/Qwen3-4B-GGUF).
Запустите сервер в отдельном терминале, затем добавьте
`--llama-port 18080` к команде анализа.

```sh
llama-server -m "$audio_work/Qwen3-4B-Q4_K_M.gguf" \
  --host 127.0.0.1 --port 18080 --ctx-size 16384 --parallel 1 --reasoning off \
  --no-ui --cors-origins localhost
```

Текстовая модель получает сегменты через loopback. Она выделяет части
речи и приводит контекстные примеры жаргона или слов-паразитов. Границы
берутся из транскрипта по номерам сегментов, цитаты сверяются с текстом.
Границы частей и классификация слов остаются подсказками для проверки.
На контрольном семинаре модель отметила ошибки транскрипта как жаргон.
Для длинных транскриптов объём входа ограничен размером контекста сервера.

## Материалы для проверки

- [Дмитрий Харакоз на лабораторном семинаре](https://commons.wikimedia.org/wiki/File:Speech_of_Dmitrii_P._Kharakoz.ogg),
  2:15, запись Владимира Архипова, CC BY-SA 3.0.
- [Доклад Vallastro о написании научных статей](https://commons.wikimedia.org/wiki/File:2026_Vallastro_presentation_on_writing_articles_on_scientific_themes_without_discussion.webm),
  24:14, запись участников онлайн-вики-конференции, CC BY-SA 4.0. Для
  коротких прогонов выбраны фрагменты 01:00–03:00 и 13:00–15:00.
- [Корпуса звучащей речи, файлы ELAN](https://spokencorpora.ru/showelan.py).
  `NDS_034-m-z`, `Sib_01-f`, `Pic-RUS_01-f_Pr-R`. Спонтанная речь
  с разметкой. Эти записи использованы как приближение к репетициям.
  Условия распространения этих материалов отдельно не подтверждены.

Для сравнения с `large-v3-turbo-q5_0` можно передать в `--model`
[мультиязычную small](https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-small.bin).
Поле `asrSeconds` отражает время запуска и работы Whisper,
`processingSeconds` включает весь анализ.

Пауза здесь означает промежуток без обнаруженной речи. В записи с
несколькими голосами метрики относятся ко всему аудио. Звуки вроде «э-э»
считаются кандидатами только при наличии в транскрипте. Голосовые
акценты и обрывы слов требуют отдельной акустической проверки.

## Первый замер на MacBook Air M2, 16 ГБ

На трёх фрагментах с ELAN сравнили транскрипты со слоем `Words`. Для
сравнения привели слова к нижнему регистру, заменили «ё» на «е» и
исключили цифры и пунктуацию. В знаменателе 533 слова по этой
токенизации.

| Модель Whisper | Словесные ошибки | Доля ошибок |
| --- | ---: | ---: |
| small | 138 | 25,9% |
| large-v3-turbo-q5_0 | 82 | 15,4% |

На шести коротких записях общей длительностью 10:35 распознавание
на Metal заняло суммарно 39,6 с у small и 72,5 с у turbo.
Полный доклад 24:14 обработан за 3:05 без этапа Qwen. Из них 2:59
заняло распознавание. Максимальный RSS по `/usr/bin/time -l` составил
1,02 ГиБ. Разбор транскрипта в Qwen занял ещё 2:43. В полном докладе
Qwen ошибочно поставил финал около 07:00, поэтому смысловую разметку
стоит проверять вручную даже при корректных ссылках на сегменты.

Из 34 размеченных пауз от 700 мс с абсолютной составляющей и без
заполнителя 29 перекрылись с детекцией VAD больше чем на половину
эталонного интервала. Из 23 размеченных заполненных пауз текстовый
детектор не отметил ни одной при обеих моделях.

## Фоновый анализ через приложение

1. Задайте один случайный секрет `AUDIO_ANALYSIS_WORKER_TOKEN` в окружении Spring
   и в терминале воркера. Для Compose переменная передаётся в контейнер `app`.
   Пустое значение закрывает внутренний API воркера.
2. Запустите Spring с Postgres и MinIO. Liquibase создаст таблицу задач.
   Запускайте воркер на хосте, где доступны Spring и адрес выдачи ссылок MinIO
   (`S3_PUBLIC_ENDPOINT`). Для локального Compose это `localhost:8081` и
   `localhost:9000`. При подключении к удалённому Spring используйте HTTPS.
3. В том же окружении, где доступны Whisper и FFmpeg, запустите:

```sh
python3 tools/audio-analysis/worker.py --base-url http://localhost:8081 \
  --model "$audio_work/ggml-large-v3-turbo-q5_0.bin" \
  --vad-model "$audio_work/ggml-silero-v6.2.0.bin"
```

Загрузите запись через `POST /api/media/upload`, передайте возвращённый
`id` как `{"mediaId":"..."}` в `POST /api/audio-analyses` и опрашивайте
`GET /api/audio-analyses/{analysisId}`. Обе операции требуют JWT владельца
медиа с доступом к API загрузки. Отчёт появится при `SUCCEEDED`, код
ошибки при `FAILED`. Удаление медиа удаляет связанные отчёты.

`GET /api/audio-analyses` возвращает последний анализ каждого собственного
файла без тяжёлого JSON-отчёта; необязательный `?mediaId=...` ограничивает
список одним файлом. Интерфейс использует этот список после обновления страницы.
Повторный `POST` для файла с активной задачей возвращает её ID, включая
одновременные запросы из разных вкладок. После завершения можно запустить
новую задачу. Для воспроизведения используется существующий
`GET /api/media/files/{id}/url`, возвращающий временную ссылку обычным текстом.

Воркер обрабатывает по одной записи, принимает MP3, WAV, OGG и M4A без
видеодорожки, до 50 МиБ и 60 минут. У анализа есть таймаут 12 минут.
При падении воркера аренда задачи истекает через 15 минут. Задача
снова доступна, пока число выдач меньше трёх. Результат из истёкшей
аренды отклоняется. Фоновый режим возвращает транскрипт, паузы, темп
и текстовые кандидаты. Разбор через Qwen доступен в ручном режиме CLI.

## Автозапуск воркера на macOS

Для воркера с Metal используйте нативный LaunchAgent на Mac. Укажите
постоянные пути к Python, моделям и утилитам. Временные файлы из
`/tmp` могут исчезнуть между запусками. В терминале должны быть
доступны `ffmpeg`, `ffprobe`, `whisper-cli` и
`whisper-vad-speech-segments`.

```sh
audio_work="$HOME/.local/share/speakers-audio-analysis"
mkdir -p "$audio_work/logs" "$HOME/Library/LaunchAgents"
token_file="$audio_work/worker.token"
(umask 077; test -e "$token_file" || openssl rand -hex 32 > "$token_file")
export AUDIO_ANALYSIS_WORKER_TOKEN="$(cat "$token_file")"

# Запустите Spring в IDE с тем же токеном и APP_AUDIO_INTERNAL_DOWNLOAD=false.
# Для готового локального стенда вместо ручной настройки используйте local.sh up.

label=club.speakers.audio-analysis
plist="$HOME/Library/LaunchAgents/$label.plist"
python3 tools/audio-analysis/launchd.py --label "$label" \
  --base-url http://localhost:8081 \
  --model "$audio_work/ggml-large-v3-turbo-q5_0.bin" \
  --vad-model "$audio_work/ggml-silero-v6.2.0.bin" \
  --token-file "$token_file" --log-dir "$audio_work/logs" > "$plist"
plutil -lint "$plist"
launchctl bootstrap "gui/$(id -u)" "$plist"
launchctl print "gui/$(id -u)/$label"
```

LaunchAgent запускается после входа пользователя в macOS. Секрет
читается из файла с приватными правами и отсутствует в plist.
Журнал лежит в `logs/audio-worker.log`. Для остановки используйте
`launchctl bootout "gui/$(id -u)/$label"`. При замене plist сначала
выгрузите старый агент, затем загрузите новый. Для удалённого Spring
нужны отдельно настроенные защищённый HTTPS-адрес API воркера и адрес
подписанных ссылок MinIO. Основной production Compose использует внутреннюю
Docker-сеть, и внешний Mac-воркер к нему по умолчанию не подключается.
На локальном Mac-стенде оба адреса указывают на localhost.

## Дополнительные эксперименты

Для длинного выступления можно добавить `--structure-mode chunks` к
команде с `--llama-port`. Qwen разбирает пятиминутные окна, а финал
проверяет отдельным запросом по последним двум минутам. Этот режим
доступен для ручного сравнения. Разметку по-прежнему следует проверять.

Акустический эксперимент с заполненными паузами запускается отдельно:

```sh
python3 tools/audio-analysis/benchmark_hesitations.py /path/to/elan/*.eaf
```

Понадобятся NumPy, FFmpeg и файлы WAV рядом с одноимёнными EAF.
Подробности и замеры собраны в [результатах повторной проверки](FOLLOWUP.md).

## Отчёт версии 2 и сравнение с Charisma Master

В версии 2 сохранены прежние поля отчёта и добавлены:

- `transcript[].words` с таймкодами слов и `timingSource`. Whisper.cpp
  выдаёт короткие элементы, которые собираются обратно в читаемые сегменты.
  Таймкоды автоматические. Если один элемент содержит несколько слов,
  их время помечается `estimated`.
- `candidates[].startMs` и `endMs` для перехода к найденной фразе.
- `tempo` с количеством слов и темпом в неперекрывающихся окнах 10 секунд.
  Последнее окно использует свою фактическую длительность. При анализе старых
  сегментов без слов временное распределение оценивается линейно и помечается
  `estimated`.
- `candidateSummary` с количеством кандидатов каждого типа, частотой на 100
  распознанных слов и перечнем повторяющихся выражений. Многословная фраза
  считается одним вхождением. Числа описывают кандидатов для переслушивания.

Идеи для временного ряда темпа, таймкодов слов и сводки кандидатов сверены с
[Charisma Master, commit b1fd473](https://github.com/desmitry/charisma-master/tree/b1fd4731f628a404022b20c1e769816e77e8ed05).
Использована собственная реализация на существующем whisper.cpp и стандартной
библиотеке Python. Новые поля описаны в OpenAPI. Ранее сохранённые отчёты
без `schemaVersion` продолжают возвращаться через API в исходном формате.

После слияния `origin/develop` на `77dfe0a` выполнены 228 Java-тестов и
10 Python-тестов. На изолированном Postgres 17 и MinIO проверены миграции
аудиоанализа вместе с новыми миграциями аудитории, загрузка M4A, выдача
задач, права владельца, ошибки аудио, старая аренда, удаление отчёта и
восстановление после перезапуска Spring через LaunchAgent.

Повторный прогон 11.11.2026 на M2 дал:

| Материал | Длительность | Полная обработка | Результат |
| --- | ---: | ---: | --- |
| Семинар Харакоза, turbo Q5 | 2:15 | 16,2 с | 279 слов, 6 пауз, 14 окон темпа |
| ELAN Pr-R, turbo Q5 | 1:15 | 8,1 с | 12 ошибок на 172 эталонных слова, 7 из 9 пауз |
| M4A в сквозном тесте, small | 25 с | 2,0 с | Отчёт версии 2 сохранён и выдан через Spring |

Для Python-проверок используйте
`python3 -B -m unittest discover -s tools/audio-analysis -p 'test_*.py'`.
