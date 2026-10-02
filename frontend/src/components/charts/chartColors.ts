// Revenue category colors: dark-mode steps of the categorical palette (blue, orange, aqua), validated for
// colour-blind separation and >= 3:1 contrast against the card surface #121826. Order is fixed: a category
// keeps its colour in every chart.
export const CATEGORY_COLORS = {
  gaming: '#3987e5',
  food: '#d95926',
  rental: '#199e70',
} as const;

/** Single-series charts use the first slot. */
export const SINGLE_SERIES = CATEGORY_COLORS.gaming;
