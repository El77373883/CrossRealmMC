# ✦ CrossRealmMC
### Bedrock ↔ Java Bridge | Sin Geyser | Sin Floodgate

> **Hecho por [soyadrianyt001](https://github.com/soyadrianyt001)**

---

## ¿Qué es CrossRealmMC?

**CrossRealmMC** es un plugin profesional para **Spigot / Paper 1.21.1** que permite a jugadores de **Minecraft Bedrock** conectarse a servidores de **Minecraft Java** sin necesidad de Geyser ni Floodgate.

Todo el bridge está construido desde cero usando **RakNet + Netty** puro.

---

## ✅ Características

- 🔗 Bridge Bedrock↔Java **100% independiente**
- 🎮 Soporte Bedrock versiones **26.0, 26.1, 26.2, 26.3**
- 👤 Detección automática: Java = `Nombre` / Bedrock = `.Nombre`
- 🔐 Modo online/offline configurable para Java **y** Bedrock
- 🛡️ Anti-cheat básico para jugadores Bedrock
- 🔨 Sistema de ban por edición (Java, Bedrock o ambos)
- 📊 Log de conexiones en archivo `.txt`
- 🌍 Idioma configurable: **Español** o **Inglés**
- 🔌 Compatible con **PlaceholderAPI**
- 🔄 Auto-restart del bridge si se cae
- 🐛 Modo debug para desarrolladores

---

## 📦 Instalación

1. Descarga `CrossRealmMC.jar` desde [Actions](../../actions)
2. Colócalo en la carpeta `plugins/` de tu servidor
3. Inicia el servidor — se generará la config automáticamente
4. Edita `plugins/CrossRealmMC/config.yml`
5. Usa `/crmc reload` para aplicar cambios

---

## ⚙️ Configuración rápida

```yaml
server:
  java-ip: "127.0.0.1"
  java-port: 25565
  bedrock-port: 19132

authentication:
  java-online-mode: true    # false = acepta Java pirata
  bedrock-online-mode: false # false = acepta Bedrock sin Xbox

language: "es"  # es = Español | en = Inglés
```

---

## 🕹️ Comandos

| Comando | Descripción |
|---|---|
| `/crmc help` | Lista de comandos |
| `/crmc info` | Info del plugin |
| `/crmc list` | Jugadores conectados |
| `/crmc stats` | Estadísticas |
| `/crmc ban <jugador> [java\|bedrock\|all]` | Banear jugador |
| `/crmc unban <jugador>` | Desbanear jugador |
| `/crmc maintenance <bedrock\|java> <on\|off>` | Modo mantenimiento |
| `/crmc reload` | Recargar configuración |

---

## 🔌 PlaceholderAPI

| Placeholder | Valor |
|---|---|
| `%crossrealmmc_edition%` | `JAVA` o `BEDROCK` |
| `%crossrealmmc_is_bedrock%` | `true` / `false` |
| `%crossrealmmc_is_java%` | `true` / `false` |
| `%crossrealmmc_bedrock_version%` | Versión Bedrock del jugador |
| `%crossrealmmc_online_bedrock%` | Jugadores Bedrock online |
| `%crossrealmmc_online_java%` | Jugadores Java online |

---

## 🛠️ Compilar desde GitHub (iPhone friendly)

1. Haz **fork** o sube el proyecto a tu GitHub
2. Ve a la pestaña **Actions**
3. Haz clic en **"Build CrossRealmMC"**
4. Clic en **"Run workflow"**
5. Descarga el `.jar` desde los **Artifacts**

---

## 📋 Requisitos

- Java 17+
- Spigot o Paper 1.21.1
- Puerto UDP 19132 abierto en tu servidor

---

## ❤️ Créditos

Desarrollado con dedicación por **soyadrianyt001**

> *"Hecho para demostrar que se puede — sin Geyser, sin Floodgate, desde cero."*
