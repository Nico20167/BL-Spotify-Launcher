package com.bluetoothspotifytrigger.spotify

import android.service.notification.NotificationListenerService

/**
 * Service VIDE : il ne lit aucune notification.
 *
 * Il n'existe que parce qu'Android conditionne l'accès aux sessions média des
 * autres apps (MediaSessionManager.getActiveSessions) à l'autorisation
 * « Accès aux notifications » accordée à un NotificationListenerService de
 * l'app. C'est ce qui permet de régler le mode aléatoire de Spotify.
 */
class MediaAccessListener : NotificationListenerService()
