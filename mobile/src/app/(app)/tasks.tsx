import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { FlatList, Linking, Platform, RefreshControl, StyleSheet, Text, View } from 'react-native';

import { Button, Card } from '@/components/ui';
import { errorMessage, type Task } from '@/lib/api';
import { confirmAction, showMessage } from '@/lib/dialogs';
import { formatDate } from '@/lib/format';
import { hasBackgroundPermission, requestLocationPermissions } from '@/lib/location';
import { useColors } from '@/lib/theme';
import { useWork } from '@/lib/work';

export default function TasksScreen() {
  const c = useColors();
  const work = useWork();
  const { loadTasks } = work;
  const [refreshing, setRefreshing] = useState(false);
  const [startingId, setStartingId] = useState<number | null>(null);

  useFocusEffect(
    useCallback(() => {
      loadTasks().catch(() => undefined);
    }, [loadTasks]),
  );

  const onRefresh = async () => {
    setRefreshing(true);
    try {
      await work.loadTasks(true);
    } catch (e) {
      showMessage('No se pudieron actualizar las tareas', errorMessage(e));
    } finally {
      setRefreshing(false);
    }
  };

  const start = async (task: Task) => {
    setStartingId(task.id);
    try {
      if (task.site?.latitude != null && Platform.OS !== 'web' && !(await hasBackgroundPermission())) {
        // Ask once, right when it matters; the timer starts either way.
        await requestLocationPermissions();
      }
      if (!work.status?.workDayOpen) {
        await work.startDay();
      }
      await work.startTimer({ type: 'TASK', taskId: task.id });
      router.navigate('/');
    } catch (e) {
      showMessage('No se pudo iniciar', errorMessage(e));
    } finally {
      setStartingId(null);
    }
  };

  const confirmStart = (task: Task) => {
    if (work.status?.timer) {
      showMessage('Ya tienes un timer corriendo', 'Detenlo en la pestaña Hoy antes de empezar otra tarea.');
      return;
    }
    if (!work.status?.workDayOpen) {
      confirmAction('Tu jornada no ha empezado', 'Se marcará tu entrada y se iniciará el timer de esta tarea.', 'Iniciar', () =>
        start(task),
      );
      return;
    }
    start(task);
  };

  const running = work.status?.timer;
  return (
    <FlatList
      style={{ backgroundColor: c.background }}
      contentContainerStyle={styles.list}
      data={work.tasks}
      keyExtractor={(t) => String(t.id)}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      ListEmptyComponent={
        <Text style={{ color: c.muted, textAlign: 'center', marginTop: 32 }}>
          No tienes tareas asignadas. Desliza hacia abajo para actualizar.
        </Text>
      }
      renderItem={({ item }) => {
        const isRunning = running?.taskId === item.id;
        const due = formatDate(item.dueAt);
        return (
          <Card>
            <Text style={[styles.title, { color: c.text }]}>{item.title}</Text>
            {item.site ? (
              <Text
                style={{ color: item.site.address ? c.primary : c.muted }}
                onPress={item.site.address ? () => openMaps(item.site?.address ?? '') : undefined}>
                {item.site.name}
                {item.site.address ? ` · ${item.site.address}` : ''}
              </Text>
            ) : null}
            {item.description ? (
              <Text style={{ color: c.muted }} numberOfLines={3}>
                {item.description}
              </Text>
            ) : null}
            <View style={styles.meta}>
              {due ? <Text style={{ color: c.muted }}>Para: {due}</Text> : null}
              {item.source !== 'local' ? <Text style={{ color: c.muted }}>{item.source}</Text> : null}
            </View>
            {isRunning ? (
              <Text style={{ color: c.success, fontWeight: '600' }}>Timer corriendo</Text>
            ) : (
              <Button title="Iniciar" onPress={() => confirmStart(item)} busy={startingId === item.id} />
            )}
          </Card>
        );
      }}
    />
  );
}

function openMaps(address: string) {
  const query = encodeURIComponent(address);
  Linking.openURL(Platform.OS === 'ios' ? `maps:?q=${query}` : `geo:0,0?q=${query}`);
}

const styles = StyleSheet.create({
  list: { padding: 16, gap: 12 },
  title: { fontSize: 18, fontWeight: '600' },
  meta: { flexDirection: 'row', gap: 12 },
});
