import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://autoyt.github.io',
  base: '/BlockEngine',
  integrations: [
    starlight({
      title: 'BlockEngine',
      description: 'Documentation for the BlockEngine Paper plugin and Java API.',
      logo: {
        src: './src/assets/logo.svg',
        alt: 'BlockEngine',
      },
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/Autoyt/BlockEngine',
        },
      ],
      customCss: ['./src/styles/custom.css'],
      sidebar: [
        {
          label: 'Start Here',
          items: [
            { label: 'Overview', link: '/BlockEngine/' },
            { label: 'How BlockEngine Works', slug: 'concepts/how-it-works' },
          ],
        },
        {
          label: 'Guides',
          autogenerate: { directory: 'guides' },
        },
        {
          label: 'Reference',
          items: [
            { label: 'Data-Driven Packs', slug: 'reference/data-driven-packs' },
            { label: 'Java API Docs', link: '/BlockEngine/api/' },
          ],
        },
      ],
    }),
  ],
});
