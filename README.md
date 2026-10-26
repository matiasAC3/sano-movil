# Sano Móvil

> **Traducimos tu celular a un lenguaje simple para que lo cuides sin miedo.**

Sano Móvil es una aplicación Android que revisa el estado del teléfono (hardware, software y privacidad) y lo explica con palabras sencillas: un puntaje de salud de 0 a 100, un semáforo de riesgos y guías paso a paso. Está pensada para adultos mayores y para personas sin conocimientos informáticos.

Proyecto del ramo **INGT1003 · Arquitectura de Desarrollo (Móvil y Web)**, Universidad Mayor. Segundo semestre 2026.

## Integrantes

| Nombre | 	Usuario de GitHub  |
|---|---|
| Matías Añasco | 
| Gabriel Riquelme | [@gabrielriquelme-boop](https://github.com/gabrielriquelme-boop)

Profesor: Carlos Muñoz.

## Prototipo navegable

- **Demo interactivo (Inicio → Escanear → Resultado → Guía):**
- [Abrir demo interactivo de la app](docs/demo.html)

- * [Probar demo interactivo en vivo](https://tu-usuario.github.io/tu-repo/demo.html)

El demo usa datos de ejemplo, porque un navegador no puede leer la batería ni los permisos reales del teléfono. La app real está en este repositorio.

## Documentos

- [Ver propuesta comercial y técnica (PDF)](Informe.pdf)
- [Presentación del Proyecto (PDF)](Presentación.pdf)
- ![Mapa del producto](docs/Mapa_producto.jpeg)
- Diagrama de arquitectura: [`docs/diagrama_arquitectura.png`](docs/diagrama_arquitectura.png)




![Arquitectura](docs/diagrama_arquitectura.png)

## Tecnologías

- Kotlin y Jetpack Compose (Android nativo, "minSdk 26").
- Datos locales en JSON ("assets/datos/"): guías, síntomas y alertas por modelo.
- Sin permiso de Internet: ningún dato sale del teléfono.
- Cuentas con Firebase Authentication y servidor propio: planificados para los próximos hitos.

## Cómo ejecutar

1. Abrir el proyecto en Android Studio.
2. Esperar la sincronización de Gradle (**File > Sync Project with Gradle Files**).
3. Ejecutar en un emulador o en un teléfono con Android 8.0 o superior.
