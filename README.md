# Grappin

Plugin Paper 1.21 : canne a peche grappin. Clic droit en visant un bloc pour etre projete vers lui.

## Commandes (`grappin.admin`)

```
/grappin give [joueur] [options]   donne un grappin
/grappin set [options]             transforme / met a jour la canne tenue en main
/grappin reload                    recharge config.yml
```

Options, combinables dans n'importe quel ordre :

| Option | Effet |
|---|---|
| `reparable` / `irreparable` | peut, ou non, etre repare (Raccommodage, enclume, meule, etabli, /repair) |
| `illimite` / `limite` | ne perd aucune durabilite en s'accrochant |
| `sans-cooldown` / `cooldown` | se relance sans temps de recharge |

Exemple : `/grappin give Steve irreparable illimite sans-cooldown`

Une option non precisee prend la valeur par defaut de `config.yml` (au give) ou garde celle du grappin (au set).

## Permissions

- `grappin.use` : utiliser un grappin (tout le monde)
- `grappin.admin` : commandes (op)
- `grappin.bypass.cooldown` : ignorer le temps de recharge

## Build

Chaque push sur `main` compile le plugin (GitHub Actions) et publie le jar dans la release **Dernier build**.
