import { Tabs } from 'expo-router';
import React from 'react';
import { Ionicons } from '@expo/vector-icons';

export default function TabLayout() {
  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: '#00D4FF',
        tabBarInactiveTintColor: '#7F8C8D',
        tabBarStyle: {
          backgroundColor: '#2C3E50',
          borderTopColor: '#1A252F',
        },
        headerStyle: {
          backgroundColor: '#2C3E50',
        },
        headerTintColor: '#ECF0F1',
        headerTitleStyle: {
          fontWeight: 'bold',
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Home',
          headerShown: true,
          tabBarIcon: ({ color }) => <Ionicons size={28} name="home" color={color} />,
        }}
      />
      <Tabs.Screen
        name="history"
        options={{
          title: 'History',
          headerShown: true,
          tabBarIcon: ({ color }) => <Ionicons size={28} name="list" color={color} />,
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          title: 'Settings',
          headerShown: true,
          tabBarIcon: ({ color }) => <Ionicons size={28} name="settings" color={color} />,
        }}
      />
    </Tabs>
  );
}
