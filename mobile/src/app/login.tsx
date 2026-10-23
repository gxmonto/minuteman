import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/ui';
import { errorMessage } from '@/lib/api';
import { DEFAULT_SERVER_URL, useSession } from '@/lib/session';
import { useColors } from '@/lib/theme';

export default function LoginScreen() {
  const c = useColors();
  const { state, signIn } = useSession();
  const [serverUrl, setServerUrl] = useState(
    (state.status === 'signedOut' && state.serverUrl) || DEFAULT_SERVER_URL,
  );
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await signIn(serverUrl, email, password);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const input = [styles.input, { color: c.text, borderColor: c.border, backgroundColor: c.card }];
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: c.background }}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
          <Text style={[styles.title, { color: c.text }]}>Minuteman</Text>
          <Text style={{ color: c.muted, marginBottom: 16 }}>Control de tiempo para técnicos de campo</Text>

          <View style={styles.field}>
            <Text style={{ color: c.muted }}>Servidor</Text>
            <TextInput
              style={input}
              value={serverUrl}
              onChangeText={setServerUrl}
              placeholder="https://minuteman.tuempresa.com"
              placeholderTextColor={c.muted}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
            />
          </View>
          <View style={styles.field}>
            <Text style={{ color: c.muted }}>Email</Text>
            <TextInput
              style={input}
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              autoComplete="email"
              keyboardType="email-address"
              textContentType="username"
            />
          </View>
          <View style={styles.field}>
            <Text style={{ color: c.muted }}>Contraseña</Text>
            <TextInput
              style={input}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoComplete="password"
              textContentType="password"
              onSubmitEditing={submit}
            />
          </View>

          {error ? <Text style={{ color: c.danger }}>{error}</Text> : null}
          <Button title="Entrar" onPress={submit} busy={busy} disabled={!serverUrl || !email || !password} />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 24, gap: 12, flexGrow: 1, justifyContent: 'center' },
  title: { fontSize: 34, fontWeight: '700' },
  field: { gap: 6 },
  input: { borderWidth: 1, borderRadius: 12, paddingHorizontal: 14, minHeight: 48, fontSize: 16 },
});
