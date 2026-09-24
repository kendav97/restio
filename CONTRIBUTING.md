# Contribuir a Restio

¡Gracias por tu interés! Esta guía explica cómo proponer cambios.

## Licencia y CLA

Restio es *source-available* bajo la [PolyForm Shield 1.0.0](LICENSE): puedes usarlo gratis,
incluso en tu restaurante, pero no ofrecer un producto que compita con él. No es una licencia
"open source" en el sentido de la OSI.

Para aceptar contribuciones externas pedimos firmar el
**[Contributor License Agreement](CLA.md)**. Conservas el copyright de tu trabajo, pero nos
concedes una licencia amplia que permite, entre otras cosas, ofrecer licencias comerciales o
cambiar la licencia del proyecto en el futuro.

La firma es automática: al abrir tu primer pull request, el bot del CLA te pedirá que publiques
este comentario en el PR:

```
I have read the CLA Document and I hereby sign the CLA
```

Solo hace falta una vez. Sin la firma, el PR no se puede fusionar.

## Antes de empezar

- Para cambios grandes, abre primero un *issue* y comenta la propuesta.
- Revisa el `README` para levantar el entorno (backend con Docker, frontend con pnpm).

## Estilo y calidad

- Código, nombres, tablas y mensajes de commit en **inglés**.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/):
  `feat(orders): add split bill`, `fix(inventory): ...`.
- Ramas `feature/<modulo>-<descripcion>` o `fix/<descripcion>` desde `main`.
- Commits pequeños que compilen; un commit por unidad lógica.
- Antes de abrir el PR deben pasar:
  - Backend: `./mvnw verify` (tests, Spotless y JaCoCo). Formato: `./mvnw spotless:apply`.
  - Frontend: `pnpm format:check`, `pnpm lint`, `pnpm typecheck` y `pnpm test`.
- Los cambios de esquema van en una migración Flyway nueva; nunca se edita una ya publicada.
- Todo texto visible para el usuario pasa por las claves de traducción de `@restio/i18n`.

## Informar de fallos de seguridad

No abras un *issue* público: escribe al mantenedor por correo privado para que podamos
corregirlo antes de divulgarlo.
