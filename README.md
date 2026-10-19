# BendyGeckoCompat

Minecraft compatibility addon bringing complete bending support to **GeckoLib 3D armors** for player animation mods (**Emotecraft**, **Player Animator**, **Mo'Bends**).

## Features
- Dynamic real-time mesh deformation and bending for GeckoLib armor cubes matching player animations.
- Full support for arms, legs, torso, and boots.
- Compatible with Minecraft 1.20.1 on both **Fabric** and **Forge**.

## Dependencies
- **[bendy-lib](https://github.com/KosmX/bendy-lib)**
- **[GeckoLib](https://github.com/bernie-g/geckolib)**

## Setup (Gradle)
```groovy
repositories {
    maven {
        url = "https://maven.pkg.github.com/Tatou8/BendyGeckoCompat"
    }
}

dependencies {
    modImplementation "fr.tatou8.bendygeckocompat:BendyGeckoCompat-fabric:1.0.0" // Fabric
    // or
    modImplementation "fr.tatou8.bendygeckocompat:BendyGeckoCompat-forge:1.0.0"  // Forge
}
```

## Credits
- **Tatou8** (Author & Maintainer)
- **KosmX** (Original bendy-lib & animation libraries)
- **GeckoLib Team** (GeckoLib animation engine)
