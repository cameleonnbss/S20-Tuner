package com.cameleonnbss.s20tuner.data

data class DebloatEntry(
    val pkg: String,
    val label: String,
    val category: String,
    val risk: String = "Safe"     // Safe / Caution
)

object DebloatList {
    val entries: List<DebloatEntry> = listOf(
        // --- Samsung apps ---
        DebloatEntry("com.samsung.android.app.spage", "Samsung Free / Spage", "Samsung"),
        DebloatEntry("com.samsung.android.calendar", "Samsung Calendar", "Samsung"),
        DebloatEntry("com.samsung.android.messaging", "Samsung Messages", "Samsung", "Caution"),
        DebloatEntry("com.sec.android.app.popupcalculator", "Samsung Calculator", "Samsung"),
        DebloatEntry("com.sec.android.app.sbrowser", "Samsung Internet", "Samsung", "Caution"),
        DebloatEntry("com.samsung.android.contacts", "Samsung Contacts", "Samsung", "Caution"),
        DebloatEntry("com.samsung.android.dialer", "Samsung Dialer", "Samsung", "Caution"),
        DebloatEntry("com.samsung.android.email.provider", "Samsung Email", "Samsung"),
        DebloatEntry("com.samsung.android.game.gamehome", "Game Launcher", "Gaming"),
        DebloatEntry("com.samsung.android.game.gametools", "Game Tools", "Gaming"),
        DebloatEntry("com.samsung.android.game.gos", "Game Optimizing Service", "Gaming", "Caution"),
        DebloatEntry("com.samsung.android.app.watchmanager", "Galaxy Wearable", "Samsung"),
        DebloatEntry("com.samsung.android.app.smartcapture", "Smart Capture", "Samsung"),
        DebloatEntry("com.samsung.android.smartswitchassistant", "Smart Switch", "Samsung"),
        DebloatEntry("com.sec.android.easyMover.Agent", "Easy Mover Agent", "Samsung"),
        DebloatEntry("com.samsung.android.mateagent", "Galaxy Friends", "Samsung"),
        DebloatEntry("com.samsung.android.scloud", "Samsung Cloud", "Samsung"),
        DebloatEntry("com.samsung.android.bixby.agent", "Bixby Voice", "Bixby"),
        DebloatEntry("com.samsung.android.bixby.wakeup", "Bixby Wakeup", "Bixby"),
        DebloatEntry("com.samsung.android.bixbyvision.framework", "Bixby Vision", "Bixby"),
        DebloatEntry("com.samsung.android.bixby.service", "Bixby Services", "Bixby"),
        DebloatEntry("com.samsung.android.bixby.es.globalaction", "Bixby Global Actions", "Bixby"),
        DebloatEntry("com.samsung.android.bixby.plmsync", "Bixby PlmSync", "Bixby"),
        DebloatEntry("com.samsung.android.visionintelligence", "Bixby Vision Intelligence", "Bixby"),
        DebloatEntry("com.samsung.android.arzone", "AR Zone", "AR"),
        DebloatEntry("com.samsung.android.aremoji", "AR Emoji", "AR"),
        DebloatEntry("com.samsung.android.aremojieditor", "AR Emoji Editor", "AR"),
        DebloatEntry("com.sec.android.mimage.avatarstickers", "AR Stickers", "AR"),
        DebloatEntry("com.samsung.android.spay", "Samsung Pay", "Samsung", "Caution"),
        DebloatEntry("com.samsung.android.spayfw", "Samsung Pay Framework", "Samsung", "Caution"),
        DebloatEntry("com.samsung.android.samsungpass", "Samsung Pass", "Samsung", "Caution"),
        DebloatEntry("com.samsung.android.samsungpassautofill", "Samsung Pass Autofill", "Samsung"),
        DebloatEntry("com.samsung.android.authfw", "Samsung Pass Auth", "Samsung", "Caution"),
        DebloatEntry("com.sec.android.diagmonagent", "Diagnostic Monitor", "Telemetry"),
        DebloatEntry("com.samsung.android.sm.devicesecurity", "Device Security", "Telemetry"),
        DebloatEntry("com.samsung.android.dqagent", "DQ Agent", "Telemetry"),
        DebloatEntry("com.sec.android.app.billing", "Samsung Billing", "Samsung", "Caution"),
        DebloatEntry("com.samsung.android.themestore", "Galaxy Themes", "Samsung"),
        DebloatEntry("com.samsung.android.galaxycontinuity", "Continuity Service", "Samsung"),
        DebloatEntry("com.samsung.android.mdx", "Link to Windows", "Samsung"),
        DebloatEntry("com.samsung.android.mdx.quickboard", "Quick Share Board", "Samsung"),
        DebloatEntry("com.samsung.android.smartthings", "SmartThings", "Samsung"),
        DebloatEntry("com.samsung.android.beaconmanager", "Beacon Manager", "Telemetry"),
        DebloatEntry("com.samsung.android.rubin.app", "Customization Service", "Telemetry"),
        DebloatEntry("com.samsung.android.odaat", "Samsung Kids", "Samsung"),
        DebloatEntry("com.samsung.android.kidsinstaller", "Kids Installer", "Samsung"),
        DebloatEntry("com.samsung.android.app.settings.bixby", "Bixby Settings", "Bixby"),
        DebloatEntry("com.samsung.systemui.bixby2", "Bixby SysUI", "Bixby"),
        DebloatEntry("com.samsung.android.cameraslicer", "Camera Slicer", "Samsung", "Caution"),
        DebloatEntry("com.samsung.android.dressroom", "Wallpaper/Outfit", "Samsung"),
        DebloatEntry("com.samsung.android.widgetapp.yahooedge", "Yahoo Widgets", "Samsung"),

        // --- Google (optional) ---
        DebloatEntry("com.google.android.apps.tachyon", "Google Duo/Meet", "Google"),
        DebloatEntry("com.google.android.videos", "Google TV", "Google"),
        DebloatEntry("com.google.android.apps.youtube.music", "YouTube Music", "Google"),
        DebloatEntry("com.google.android.feedback", "Google Feedback", "Telemetry"),
        DebloatEntry("com.google.android.printservice.recommendation", "Print Service Rec", "Google"),
        DebloatEntry("com.google.android.apps.maps", "Google Maps", "Google", "Caution"),
        DebloatEntry("com.google.android.googlequicksearchbox", "Google Search Widget", "Google", "Caution"),

        // --- Microsoft / Facebook / carriers ---
        DebloatEntry("com.microsoft.skydrive", "OneDrive", "Third party"),
        DebloatEntry("com.microsoft.office.officehubrow", "MS Office", "Third party"),
        DebloatEntry("com.microsoft.appmanager", "Link to Windows Service", "Third party"),
        DebloatEntry("com.facebook.katana", "Facebook", "Third party"),
        DebloatEntry("com.facebook.appmanager", "Facebook App Manager", "Third party"),
        DebloatEntry("com.facebook.system", "Facebook Installer", "Third party"),
        DebloatEntry("com.facebook.services", "Facebook Services", "Third party"),
        DebloatEntry("com.netflix.mediaclient", "Netflix", "Third party"),
        DebloatEntry("com.spotify.music", "Spotify", "Third party"),
        DebloatEntry("com.linkedin.android", "LinkedIn", "Third party")
    )

    val categories = listOf("All", "Samsung", "Bixby", "AR", "Google", "Telemetry", "Third party", "Gaming")
}
