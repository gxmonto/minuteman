import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, Linking, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { Banner, Button, Card } from '@/components/ui';
import { errorMessage } from '@/lib/api';
import { confirmAction, showMessage } from '@/lib/dialogs';
import { formatDuration, formatHours, formatTime, secondsSince } from '@/lib/format';
import { requestLocationPermissions } from '@/lib/location';
import { useSession } from '@/lib/session';
import { useColors } from '@/lib/theme';
import { useTicker, useWork } from '@/lib/work';

export default function TodayScreen() {
  const c = useColors();
  const { state, signOut } = useSession();
  const work = useWork();
  const { status, clockOffsetMs } = work;
  const timer = status?.timer ?? null;
  const now = useTicker(Boolean(status?.workDayOpen));
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const run = async (key: string, action: () => Promise<void>) => {
    setBusy(key);
    try {
      await action();
    } catch (e) {
      showMessage('No se pudo completar', errorMessage(e));
    } finally {
      setBusy(null);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await work.refreshStatus().catch((e) => showMessage('Sin conexión', errorMessage(e)));
    setRefreshing(false);
  };

  const enableBackgroundLocation = async () => {
    const result = await requestLocationPermissions();
    if (!result.background) {
      Alert.alert(
        'Ubicación "Siempre" desactivada',
        'Para avisarte si te vas con el timer abierto, permite la ubicación "Todo el tiempo" en Ajustes.',
        [{ text: 'Ahora no' }, { text: 'Abrir Ajustes', onPress: () => Linking.openSettings() }],
      );
    }
    await work.refreshStatus().catch(() => undefined);
  };

  const confirmEndDay = () =>
    confirmAction(
      'Terminar jornada',
      timer ? 'También se detendrá el timer que está corriendo.' : '¿Marcar tu salida?',
      'Terminar',
      () => run('endDay', work.endDay),
    );

  const userName = state.status === 'signedIn' ? state.user.fullName || state.user.email : '';
  const runningSeconds = timer ? secondsSince(timer.startTime, clockOffsetMs, now) : 0;
  const closedSeconds = status ? status.totalSecondsToday - status.secondsRunning : 0;

  return (
    <ScrollView
      style={{ backgroundColor: c.background }}
      contentContainerStyle={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}>
      {work.loading && !status ? (
        <Text style={{ color: c.muted }}>Cargando…</Text>
      ) : !status?.workDayOpen ? (
        <Card>
          <Text style={[styles.heading, { color: c.text }]}>Hola, {userName}</Text>
          <Text style={{ color: c.muted }}>Tu jornada no ha empezado.</Text>
          <Button title="Iniciar jornada" onPress={() => run('startDay', work.startDay)} busy={busy === 'startDay'} />
        </Card>
      ) : (
        <>
          <Card>
            <Text style={{ color: c.muted }}>Jornada desde las {formatTime(status.workDay?.clockIn)}</Text>
            <Text style={[styles.big, { color: c.text }]}>{formatHours(closedSeconds + runningSeconds)}</Text>
            <Text style={{ color: c.muted }}>registradas hoy</Text>
          </Card>

          {timer ? (
            <Card>
              <Text style={{ color: c.muted }}>{timer.type === 'TASK' ? 'Trabajando en' : 'En traslado'}</Text>
              <Text style={[styles.heading, { color: c.text }]}>
                {timer.taskTitle ?? (timer.type === 'TRAVEL' ? 'Traslado' : 'Tarea')}
              </Text>
              {timer.site ? <Text style={{ color: c.muted }}>{timer.site.name}</Text> : null}
              <Text style={[styles.timer, { color: c.success }]}>{formatDuration(runningSeconds)}</Text>

              {timer.leftSiteAt ? (
                <Banner>Saliste del sitio a las {formatTime(timer.leftSiteAt)}. ¿Ya terminaste?</Banner>
              ) : null}
              {timer.type === 'TASK' && work.geofence === 'no-permission' ? (
                <Banner action={<Button title="Activar" variant="secondary" onPress={enableBackgroundLocation} />}>
                  Activa la ubicación &quot;Siempre&quot; para avisarte si te vas con el timer abierto.
                </Banner>
              ) : null}

              <TextInput
                style={[styles.notes, { color: c.text, borderColor: c.border }]}
                placeholder="Notas (opcional)"
                placeholderTextColor={c.muted}
                value={notes}
                onChangeText={setNotes}
                multiline
                maxLength={2000}
              />
              <Button
                title="Detener timer"
                variant="danger"
                busy={busy === 'stop'}
                onPress={() =>
                  run('stop', async () => {
                    await work.stopTimer(notes.trim() || null);
                    setNotes('');
                  })
                }
              />
            </Card>
          ) : (
            <Card>
              <Text style={[styles.heading, { color: c.text }]}>¿Qué estás haciendo?</Text>
              <Button title="Elegir tarea" onPress={() => router.navigate('/tasks')} />
              <Button
                title="Iniciar traslado"
                variant="secondary"
                busy={busy === 'travel'}
                onPress={() => run('travel', () => work.startTimer({ type: 'TRAVEL' }))}
              />
            </Card>
          )}

          <Button title="Terminar jornada" variant="secondary" onPress={confirmEndDay} busy={busy === 'endDay'} />
        </>
      )}

      <View style={styles.footer}>
        <Text style={{ color: c.muted }}>{userName}</Text>
        <Button title="Cerrar sesión" variant="secondary" onPress={() => run('signOut', signOut)} />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 16 },
  heading: { fontSize: 22, fontWeight: '600' },
  big: { fontSize: 36, fontWeight: '700' },
  timer: { fontSize: 48, fontWeight: '700', fontVariant: ['tabular-nums'] },
  notes: { borderWidth: 1, borderRadius: 12, padding: 12, minHeight: 64, textAlignVertical: 'top' },
  footer: { marginTop: 24, gap: 8 },
});
