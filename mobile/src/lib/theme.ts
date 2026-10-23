import { useColorScheme } from 'react-native';

const palette = {
  light: {
    background: '#F4F5F7',
    card: '#FFFFFF',
    text: '#14171A',
    muted: '#5F6670',
    border: '#E1E4E8',
    primary: '#1463FF',
    primaryText: '#FFFFFF',
    success: '#13824B',
    danger: '#C62828',
    warningBg: '#FFF4D6',
    warningText: '#7A5200',
  },
  dark: {
    background: '#0E1013',
    card: '#1A1D21',
    text: '#F2F4F7',
    muted: '#A2A9B3',
    border: '#2A2F35',
    primary: '#4D8BFF',
    primaryText: '#FFFFFF',
    success: '#3CCB7F',
    danger: '#FF6B6B',
    warningBg: '#3A2E10',
    warningText: '#FFD27A',
  },
};

export type Colors = typeof palette.light;

export function useColors(): Colors {
  return useColorScheme() === 'dark' ? palette.dark : palette.light;
}
